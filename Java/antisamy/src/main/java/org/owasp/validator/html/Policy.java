/*
 * Copyright (c) 2007-2013, Arshan Dabirsiaghi, Jason Li, Kristian Rosenvold
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of OWASP nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.owasp.validator.html;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.owasp.validator.html.model.AntiSamyPattern;
import org.owasp.validator.html.model.Attribute;
import org.owasp.validator.html.model.Property;
import org.owasp.validator.html.model.Tag;
import org.owasp.validator.html.scan.Constants;
import org.owasp.validator.html.util.URIUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.owasp.validator.html.util.XMLUtil.getAttributeValue;

/**
 * Policy.java
 * <p/>
 * This file holds the model for our policy engine.
 *
 * @author Arshan Dabirsiaghi
 */

public class Policy {

    public static final Pattern ANYTHING_REGEXP = Pattern.compile(".*");

    protected static final String DEFAULT_POLICY_URI = "resources/antisamy.xml";
    private static final String DEFAULT_ONINVALID = "removeAttribute";

    public static final int DEFAULT_MAX_INPUT_SIZE = 100000;
    public static final int DEFAULT_MAX_STYLESHEET_IMPORTS = 1;

    public static final String OMIT_XML_DECLARATION = "omitXmlDeclaration";
    public static final String OMIT_DOCTYPE_DECLARATION = "omitDoctypeDeclaration";
    public static final String USE_XHTML = "useXHTML";
    public static final String FORMAT_OUTPUT = "formatOutput";
    public static final String EMBED_STYLESHEETS = "embedStyleSheets";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String ANCHORS_NOFOLLOW = "nofollowAnchors";
    public static final String VALIDATE_PARAM_AS_EMBED = "validateParamAsEmbed";
    public static final String PRESERVE_SPACE = "preserveSpace";
    public static final String PRESERVE_COMMENTS = "preserveComments";
    public static final String ENTITY_ENCODE_INTL_CHARS = "entityEncodeIntlChars";

    public static final String ACTION_VALIDATE = "validate";
    public static final String ACTION_FILTER = "filter";
    public static final String ACTION_TRUNCATE = "truncate";

    private static char REGEXP_BEGIN = '^';
    private static char REGEXP_END = '$';

    private final Map<String, AntiSamyPattern> commonRegularExpressions;
    protected final Map<String, Tag> tagRules;
    private final Map<String, Property> cssRules;
    protected final Map<String, String> directives;
    private final Map<String, Attribute> globalAttributes;

    private final TagMatcher allowedEmptyTagsMatcher;
    private final TagMatcher requiresClosingTagsMatcher;

    /**
     * The path to the base policy file, used to resolve relative paths when reading included files
     */
    protected static URL baseUrl = null; // Todo: Make not static

    /**
     * Retrieves a Tag from the Policy.
     *
     * @param tagName The name of the Tag to look up.
     * @return The Tag associated with the name specified, or null if none is found.
     */
    public Tag getTagByName(String tagName) {
        return tagRules.get(tagName.toLowerCase());
    }



    protected static class ParseContext {
        Map<String, AntiSamyPattern> commonRegularExpressions = new HashMap<String, AntiSamyPattern>();
        Map<String, Attribute> commonAttributes = new HashMap<String, Attribute>();
        Map<String, Tag> tagRules = new HashMap<String, Tag>();
        Map<String, Property> cssRules = new HashMap<String, Property>();
        Map<String, String> directives = new HashMap<String, String>();
        Map<String, Attribute> globalAttributes = new HashMap<String, Attribute>();

        List<String> allowedEmptyTags = new ArrayList<String>();
        List<String> requireClosingTags = new ArrayList<String>();

        public void resetParamsWhereLastConfigWins() {
            allowedEmptyTags.clear();
            requireClosingTags.clear();
        }
    }


    /**
     * Retrieves a CSS Property from the Policy.
     *
     * @param propertyName The name of the CSS Property to look up.
     * @return The CSS Property associated with the name specified, or null if none is found.
     */
    public Property getPropertyByName(String propertyName) {
        return cssRules.get(propertyName.toLowerCase());
    }

    /**
     * This retrieves a Policy based on a default location ("resources/antisamy.xml")
     *
     * @return A populated Policy object based on the XML policy file located in the default location.
     * @throws PolicyException If the file is not found or there is a problem parsing the file.
     */
    public static Policy getInstance() throws PolicyException {
        return getInstance(DEFAULT_POLICY_URI);
    }

    /**
     * This retrieves a Policy based on the file name passed in
     *
     * @param filename The path to the XML policy file.
     * @return A populated Policy object based on the XML policy file located in the location passed in.
     * @throws PolicyException If the file is not found or there is a problem parsing the file.
     */
    public static Policy getInstance(String filename) throws PolicyException {
        File file = new File(filename);
        return getInstance(file);
    }

    /**
     * This retrieves a Policy based on the File object passed in
     *
     * @param file A File object which contains the XML policy information.
     * @return A populated Policy object based on the XML policy file pointed to by the File parameter.
     * @throws PolicyException If the file is not found or there is a problem parsing the file.
     */
    public static Policy getInstance(File file) throws PolicyException {
        try {
            URI uri = file.toURI();
            return getInstance(uri.toURL());
        } catch (IOException e) {
            throw new PolicyException(e);
        }
    }


    /**
     * This retrieves a Policy based on the URL object passed in.
     * <p/>
     * NOTE: This is the only factory method that will work with <include> tags
     * in AntiSamy policy files.
     *
     * @param url A URL object which contains the XML policy information.
     * @return A populated Policy object based on the XML policy file pointed to by the File parameter.
     * @throws PolicyException If the file is not found or there is a problem parsing the file.
     */
    public static Policy getInstance(URL url) throws PolicyException {

        if (baseUrl == null) setBaseURL(url);
        return new Policy(getParseContext(getTopLevelElement(url)));
    }


    protected Policy(ParseContext parseContext) throws PolicyException {
        this.allowedEmptyTagsMatcher = new TagMatcher(parseContext.allowedEmptyTags);
        this.requiresClosingTagsMatcher = new TagMatcher(parseContext.requireClosingTags);
        this.commonRegularExpressions = Collections.unmodifiableMap(parseContext.commonRegularExpressions);
        this.tagRules = Collections.unmodifiableMap(parseContext.tagRules);
        this.cssRules = Collections.unmodifiableMap(parseContext.cssRules);
        this.directives = Collections.unmodifiableMap(parseContext.directives);
        this.globalAttributes = Collections.unmodifiableMap(parseContext.globalAttributes);
    }

    protected Policy(Policy old, Map<String, String> directives, Map<String, Tag> tagRules) {
        this.allowedEmptyTagsMatcher = old.allowedEmptyTagsMatcher;
        this.requiresClosingTagsMatcher = old.requiresClosingTagsMatcher;
        this.commonRegularExpressions = old.commonRegularExpressions;
        this.tagRules = tagRules;
        this.cssRules = old.cssRules;
        this.directives = directives;
        this.globalAttributes = old.globalAttributes;
    }

    protected static ParseContext getSimpleParseContext(Element topLevelElement) throws PolicyException {
        ParseContext parseContext = new ParseContext();
        /**
         * Parse the top level element itself
         */
        parsePolicy(topLevelElement, parseContext);
        return parseContext;
    }

    protected static ParseContext getParseContext(Element topLevelElement) throws PolicyException {
        ParseContext parseContext = new ParseContext();

        /**
         * Are there any included policies? These are parsed here first so that
         * rules in _this_ policy file will override included rules.
         *
         * NOTE that by this being here we only support one level of includes.
         * To support recursion, move this into the parsePolicy method.
         */
        for (Element include : getByTagName(topLevelElement, "include")) {
            String href = getAttributeValue(include, "href");

            Element includedPolicy = getPolicy(href);
            parsePolicy(includedPolicy, parseContext);
        }

        parsePolicy(topLevelElement, parseContext);
        return parseContext;
    }


    protected static Element getTopLevelElement(URL url) throws PolicyException {
        try {
            InputSource source = resolveEntity(url.toExternalForm());
            if (source == null) {
                source = new InputSource(url.toExternalForm());
                source.setByteStream(url.openStream());
            } else {
                source.setSystemId(url.toExternalForm());
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.parse(source);
            return dom.getDocumentElement();
        } catch (SAXException e) {
            throw new PolicyException(e);
        } catch (ParserConfigurationException e) {
            throw new PolicyException(e);
        } catch (IOException e) {
            throw new PolicyException(e);
        }
    }


    private static void parsePolicy(Element topLevelElement, ParseContext parseContext)
            throws PolicyException {

        if (topLevelElement == null) return;

        parseContext.resetParamsWhereLastConfigWins();

        parseCommonRegExps(getFirstChild(topLevelElement, "common-regexps"), parseContext.commonRegularExpressions);
        parseDirectives(getFirstChild(topLevelElement, "directives"), parseContext.directives);
        parseCommonAttributes(getFirstChild(topLevelElement, "common-attributes"), parseContext.commonAttributes, parseContext.commonRegularExpressions);
        parseGlobalAttributes(getFirstChild(topLevelElement, "global-tag-attributes"), parseContext.globalAttributes, parseContext.commonAttributes);
        parseTagRules(getFirstChild(topLevelElement, "tag-rules"), parseContext.commonAttributes, parseContext.commonRegularExpressions, parseContext.tagRules);
        parseCSSRules(getFirstChild(topLevelElement, "css-rules"), parseContext.cssRules, parseContext.commonRegularExpressions);

        parseAllowedEmptyTags(getFirstChild(topLevelElement, "allowed-empty-tags"), parseContext.allowedEmptyTags);
        parseRequiresClosingTags(getFirstChild(topLevelElement, "require-closing-tags"), parseContext.requireClosingTags);
    }


    /**
     * Returns the top level element of a loaded policy Document
     */
    private static Element getPolicy(String href)
            throws PolicyException {

        try {
            InputSource source = null;

            // Can't resolve public id, but might be able to resolve relative
            // system id, since we have a base URI.
            if (href != null && baseUrl != null) {
                URL url;

                try {
                    url = new URL(baseUrl, href);
                    source = new InputSource(url.openStream());
                    source.setSystemId(href);

                } catch (MalformedURLException except) {
                    try {
                        String absURL = URIUtils.resolveAsString(href, baseUrl.toString());
                        url = new URL(absURL);
                        source = new InputSource(url.openStream());
                        source.setSystemId(href);

                    } catch (MalformedURLException ex2) {
                        // nothing to do
                    }

                } catch (java.io.FileNotFoundException fnfe) {
                    try {
                        String absURL = URIUtils.resolveAsString(href, baseUrl.toString());
                        url = new URL(absURL);
                        source = new InputSource(url.openStream());
                        source.setSystemId(href);

                    } catch (MalformedURLException ex2) {
                        // nothing to do
                    }
                }
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom;

            /**
             * Load and parse the file.
             */
            if (source != null) {
                dom = db.parse(source);


                /**
                 * Get the policy information out of it!
                 */

                return dom.getDocumentElement();
            }

            return null;
        } catch (SAXException e) {
            throw new PolicyException(e);
        } catch (ParserConfigurationException e) {
            throw new PolicyException(e);
        } catch (IOException e) {
            throw new PolicyException(e);
        }
    }


    /**
     * Go through <directives> section of the policy file.
     *
     * @param root       Top level of <directives>
     * @param directives The directives map to update
     */
    private static void parseDirectives(Element root, Map<String, String> directives) {
        for (Element ele : getByTagName(root, "directive")) {
            String name = getAttributeValue(ele, "name");
            String value = getAttributeValue(ele, "value");
            directives.put(name, value);
        }
    }


    /**
     * Go through <allowed-empty-tags> section of the policy file.
     *
     * @param allowedEmptyTagsListNode Top level of <allowed-empty-tags>
     * @param allowedEmptyTags         The tags that can be empty
     */
    private static void parseAllowedEmptyTags(Element allowedEmptyTagsListNode, List<String> allowedEmptyTags) throws PolicyException {
        if (allowedEmptyTagsListNode != null) {
            for (Element literalNode : getGrandChildrenByTagName(allowedEmptyTagsListNode, "literal-list", "literal")) {

                String value = getAttributeValue(literalNode, "value");

                if (value != null && value.length() > 0) {
                    allowedEmptyTags.add(value);
                }
            }
        } else {
            allowedEmptyTags.addAll(Constants.defaultAllowedEmptyTags);
        }
    }

    /**
     * Go through <require-closing-tags> section of the policy file.
     *
     * @param requiresClosingTagsListNode Top level of <require-closing-tags>
     * @param requiresClosingTags         The list of tags that require closing
     */
    private static void parseRequiresClosingTags(Element requiresClosingTagsListNode, List<String> requiresClosingTags) throws PolicyException {
        if (requiresClosingTagsListNode != null) {
            for (Element literalNode : getGrandChildrenByTagName(requiresClosingTagsListNode, "literal-list", "literal")) {

                String value = getAttributeValue(literalNode, "value");

                if (value != null && value.length() > 0) {
                    requiresClosingTags.add(value);
                }
            }
        } else {
            requiresClosingTags.addAll(Constants.defaultRequiresClosingTags);
        }
    }

    /**
     * Go through <global-tag-attributes> section of the policy file.
     *
     * @param root              Top level of <global-tag-attributes>
     * @param globalAttributes1 A HashMap of global Attributes that need validation for every tag.
     * @param commonAttributes  The common attributes
     * @throws PolicyException
     */
    private static void parseGlobalAttributes(Element root, Map<String, Attribute> globalAttributes1, Map<String, Attribute> commonAttributes) throws PolicyException {
        for (Element ele : getByTagName(root, "attribute")) {

            String name = getAttributeValue(ele, "name");

            Attribute toAdd = commonAttributes.get(name.toLowerCase());

            if (toAdd != null) {
                globalAttributes1.put(name.toLowerCase(), toAdd);
            } else {
                throw new PolicyException("Global attribute '" + name + "' was not defined in <common-attributes>");
            }
        }
    }

    /**
     * Go through the <common-regexps> section of the policy file.
     *
     * @param root                      Top level of <common-regexps>
     * @param commonRegularExpressions1 the antisamy pattern objects
     */
    private static void parseCommonRegExps(Element root, Map<String, AntiSamyPattern> commonRegularExpressions1) {
        for (Element ele : getByTagName(root, "regexp")) {

            String name = getAttributeValue(ele, "name");
            Pattern pattern = Pattern.compile(getAttributeValue(ele, "value"));

            commonRegularExpressions1.put(name, new AntiSamyPattern(pattern));
        }
    }


    private static void parseCommonAttributes(Element root, Map<String, Attribute> commonAttributes1, Map<String, AntiSamyPattern> commonRegularExpressions1) {
        for (Element ele : getByTagName(root, "attribute")) {

            String onInvalid = getAttributeValue(ele, "onInvalid");
            String name = getAttributeValue(ele, "name");

            List<Pattern> allowedRegexps = getAllowedRegexps(commonRegularExpressions1, ele);
            List<String> allowedValues = getAllowedLiterals(ele);

            final String onInvalidStr;
            if (onInvalid != null && onInvalid.length() > 0) {
                onInvalidStr = onInvalid;
            } else {
                onInvalidStr = DEFAULT_ONINVALID;
            }
            String description = getAttributeValue(ele, "description");
            Attribute attribute = new Attribute(getAttributeValue(ele, "name"), allowedRegexps, allowedValues, onInvalidStr, description);


            commonAttributes1.put(name.toLowerCase(), attribute);
        }
    }

    private static List<String> getAllowedLiterals(Element ele) {
        List<String> allowedValues = new ArrayList<String>();
        for (Element literalNode : getGrandChildrenByTagName(ele, "literal-list", "literal")) {
            String value = getAttributeValue(literalNode, "value");

            if (value != null && value.length() > 0) {
                allowedValues.add(value);
            } else if (literalNode.getNodeValue() != null) {
                allowedValues.add(literalNode.getNodeValue());
            }

        }
        return allowedValues;
    }

    private static List<Pattern> getAllowedRegexps(Map<String, AntiSamyPattern> commonRegularExpressions1, Element ele) {
        List<Pattern> allowedRegExp = new ArrayList<Pattern>();
        for (Element regExpNode : getGrandChildrenByTagName(ele, "regexp-list", "regexp")) {
            String regExpName = getAttributeValue(regExpNode, "name");
            String value = getAttributeValue(regExpNode, "value");

            if (regExpName != null && regExpName.length() > 0) {
                allowedRegExp.add(commonRegularExpressions1.get(regExpName).getPattern());
            } else {
                allowedRegExp.add(Pattern.compile(REGEXP_BEGIN + value + REGEXP_END));
            }
        }
        return allowedRegExp;
    }

    private static List<Pattern> getAllowedRegexps2(Map<String, AntiSamyPattern> commonRegularExpressions1, Element attributeNode, String tagName) throws PolicyException {
        List<Pattern> allowedRegexps = new ArrayList<Pattern>();
        for (Element regExpNode : getGrandChildrenByTagName(attributeNode, "regexp-list", "regexp")) {
            String regExpName = getAttributeValue(regExpNode, "name");
            String value = getAttributeValue(regExpNode, "value");

            /*
            * Look up common regular expression specified
            * by the "name" field. They can put a common
            * name in the "name" field or provide a custom
            * value in the "value" field. They must choose
            * one or the other, not both.
            */
            if (regExpName != null && regExpName.length() > 0) {

                AntiSamyPattern pattern = commonRegularExpressions1.get(regExpName);
                if (pattern != null) {
                    allowedRegexps.add(pattern.getPattern());
                } else {
                    throw new PolicyException("Regular expression '" + regExpName + "' was referenced as a common regexp in definition of '" + tagName + "', but does not exist in <common-regexp>");
                }

            } else if (value != null && value.length() > 0) {
                allowedRegexps.add(Pattern.compile(REGEXP_BEGIN + value + REGEXP_END));
            }
        }
        return allowedRegexps;
    }

    private static List<Pattern> getAllowedRegexp3(Map<String, AntiSamyPattern> commonRegularExpressions1, Element ele, String name) throws PolicyException {
        List<Pattern> allowedRegExp = new ArrayList<Pattern>();
        for (Element regExpNode : getGrandChildrenByTagName(ele, "regexp-list", "regexp")) {
            String regExpName = getAttributeValue(regExpNode, "name");
            String value = getAttributeValue(regExpNode, "value");

            AntiSamyPattern pattern = commonRegularExpressions1.get(regExpName);

            if (pattern != null) {
                allowedRegExp.add(pattern.getPattern());
            } else if (value != null) {
                allowedRegExp.add(Pattern.compile(REGEXP_BEGIN + value + REGEXP_END));
            } else {
                throw new PolicyException("Regular expression '" + regExpName + "' was referenced as a common regexp in definition of '" + name + "', but does not exist in <common-regexp>");
            }
        }
        return allowedRegExp;
    }



    private static void parseTagRules(Element root, Map<String, Attribute> commonAttributes1, Map<String, AntiSamyPattern> commonRegularExpressions1, Map<String, Tag> tagRules1) throws PolicyException {

        if (root == null) return;

        for (Element tagNode : getByTagName(root, "tag")) {

            String name = getAttributeValue(tagNode, "name");
            String action = getAttributeValue(tagNode, "action");

            NodeList attributeList = tagNode.getElementsByTagName("attribute");
            Map<String, Attribute> tagAttributes = getTagAttributes(commonAttributes1, commonRegularExpressions1, attributeList, name);
            Tag tag = new Tag(name, tagAttributes, action);

            tagRules1.put(name.toLowerCase(), tag);
        }
    }

    private static Map<String, Attribute> getTagAttributes(Map<String, Attribute> commonAttributes1, Map<String, AntiSamyPattern> commonRegularExpressions1, NodeList attributeList, String tagName) throws PolicyException {
        Map<String,Attribute> tagAttributes = new HashMap<String, Attribute>();
        for (int j = 0; j < attributeList.getLength(); j++) {

            Element attributeNode = (Element) attributeList.item(j);

            String attrName = getAttributeValue(attributeNode, "name").toLowerCase();
            if (!attributeNode.hasChildNodes()) {

                Attribute attribute = commonAttributes1.get(attrName);

                /*
                      * All they provided was the name, so they must want a common
                      * attribute.
                      */
                if (attribute != null) {

                    /*
                           * If they provide onInvalid/description values here they will
                           * override the common values.
                           */

                    String onInvalid = getAttributeValue(attributeNode, "onInvalid");
                    String description = getAttributeValue(attributeNode, "description");

                    Attribute changed = attribute.mutate(onInvalid, description);

                    commonAttributes1.put(attrName, changed);

                    tagAttributes.put(attrName, changed);

                } else {

                    throw new PolicyException("Attribute '" + getAttributeValue(attributeNode, "name") + "' was referenced as a common attribute in definition of '" + tagName + "', but does not exist in <common-attributes>");

                }

            } else {
                List<Pattern> allowedRegexps2 = getAllowedRegexps2(commonRegularExpressions1, attributeNode, tagName);
                List<String> allowedValues2 = getAllowedLiterals(attributeNode);
                String onInvalid = getAttributeValue(attributeNode, "onInvalid");
                String description = getAttributeValue(attributeNode, "description");
                Attribute attribute = new Attribute(getAttributeValue(attributeNode, "name"), allowedRegexps2, allowedValues2, onInvalid, description);

                /*
                      * Add fully built attribute.
                      */
                tagAttributes.put(attrName, attribute);
            }

        }
        return tagAttributes;
    }


    private static void parseCSSRules(Element root, Map<String, Property> cssRules1, Map<String, AntiSamyPattern> commonRegularExpressions1) throws PolicyException {

        for (Element ele : getByTagName(root, "property")){

            String name = getAttributeValue(ele, "name");
            String description = getAttributeValue(ele, "description");


            List<Pattern> allowedRegexp3 = getAllowedRegexp3(commonRegularExpressions1, ele, name);

            List<String> allowedValue = new ArrayList<String>();
            for (Element literalNode : getGrandChildrenByTagName(ele, "literal-list", "literal")) {
                allowedValue.add(getAttributeValue(literalNode, "value"));
            }

            List<String> shortHandRefs = new ArrayList<String>();
            for (Element shorthandNode : getGrandChildrenByTagName(ele, "shorthand-list", "shorthand")) {
                shortHandRefs.add(getAttributeValue(shorthandNode, "name"));
            }

            String onInvalid = getAttributeValue(ele, "onInvalid");
            final String onInvalidStr;
            if (onInvalid != null && onInvalid.length() > 0) {
                onInvalidStr = onInvalid;
            } else {
                onInvalidStr = DEFAULT_ONINVALID;
            }
            Property property = new Property(name,allowedRegexp3, allowedValue, shortHandRefs, description, onInvalidStr );



            cssRules1.put(name.toLowerCase(), property);

        }
    }


    /**
     * A simple method for returning on of the <global-attribute> entries by
     * name.
     *
     * @param name The name of the global-attribute we want to look up.
     * @return An Attribute associated with the global-attribute lookup name specified.
     */
    public Attribute getGlobalAttributeByName(String name) {
        return globalAttributes.get(name.toLowerCase());

    }

    /**
     * Return all the allowed empty tags configured in the Policy.
     *
     * @return A String array of all the he allowed empty tags configured in the Policy.
     */
    public TagMatcher getAllowedEmptyTags() {
        return allowedEmptyTagsMatcher;
    }

    /**
     * Return all the tags that are required to be closed with an end tag, even if they have no child content.
     *
     * @return A String array of all the tags that are required to be closed with an end tag, even if they have no child content.
     */
    public TagMatcher getRequiresClosingTags() {
        return requiresClosingTagsMatcher;
    }

    /**
     * Return a directive value based on a lookup name.
     *
     * @return A String object containing the directive associated with the lookup name, or null if none is found.
     */
    public String getDirective(String name) {
        return directives.get(name);
    }


    /**
     * Returns the maximum input size. If this value is not specified by
     * the policy, the <code>DEFAULT_MAX_INPUT_SIZE</code> is used.
     *
     * @return the maximium input size.
     */
    public int getMaxInputSize() {
        int maxInputSize = Policy.DEFAULT_MAX_INPUT_SIZE;

        try {
            maxInputSize = Integer.parseInt(getDirective("maxInputSize"));
        } catch (NumberFormatException ignore) {
        }

        return maxInputSize;
    }

    /**
     * Set the base directory to use to resolve relative file paths when including other policy files.
     *
     * @param newValue The new base url
     */
    public static void setBaseURL(URL newValue) {
        baseUrl = newValue;
    }

    /**
     * Resolves public & system ids to files stored within the JAR.
     */
    public static InputSource resolveEntity(final String systemId) throws IOException, SAXException {
        InputSource source;

        // Can't resolve public id, but might be able to resolve relative
        // system id, since we have a base URI.
        if (systemId != null && baseUrl != null) {
            URL url;

            try {
                url = new URL(baseUrl, systemId);
                source = new InputSource(url.openStream());
                source.setSystemId(systemId);
                return source;
            } catch (MalformedURLException except) {
                try {
                    String absURL = URIUtils.resolveAsString(systemId, baseUrl.toString());
                    url = new URL(absURL);
                    source = new InputSource(url.openStream());
                    source.setSystemId(systemId);
                    return source;
                } catch (MalformedURLException ex2) {
                    // nothing to do
                }
            } catch (java.io.FileNotFoundException fnfe) {
                try {
                    String absURL = URIUtils.resolveAsString(systemId, baseUrl.toString());
                    url = new URL(absURL);
                    source = new InputSource(url.openStream());
                    source.setSystemId(systemId);
                    return source;
                } catch (MalformedURLException ex2) {
                    // nothing to do
                }
            }
            return null;
        }

        // No resolving.
        return null;
    }

    private static Element getFirstChild(Element element, String tagName) {
        if (element == null) return null;
        NodeList elementsByTagName = element.getElementsByTagName(tagName);
        if (elementsByTagName != null && elementsByTagName.getLength() > 0)
            return (Element) elementsByTagName.item(0);
        else
            return null;
    }

    private static Iterable<Element>  getGrandChildrenByTagName(Element parent, String immediateChildName, String subChild){
        NodeList elementsByTagName = parent.getElementsByTagName(immediateChildName);
        if (elementsByTagName.getLength() == 0) return Collections.emptyList();
        Element regExpListNode = (Element) elementsByTagName.item(0);
        return getByTagName( regExpListNode, subChild);
    }

    private static Iterable<Element> getByTagName(Element parent, String tagName) {
        if (parent == null) {
            return Collections.emptyList();
        }
        final NodeList nodes = parent.getElementsByTagName(tagName);
        return new Iterable<Element>() {
            public Iterator<Element> iterator() {
                return new Iterator<Element>() {
                    int pos = 0;
                    int len = nodes.getLength();

                    public boolean hasNext() {
                        return pos < len;
                    }

                    public Element next() {
                        return (Element) nodes.item(pos++);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Cant remove");
                    }
                };
            }
        };
    }

    public AntiSamyPattern getCommonRegularExpressions(String name) {
        return commonRegularExpressions.get(name);
    }

}
