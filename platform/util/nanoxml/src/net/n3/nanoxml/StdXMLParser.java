/* StdXMLParser.java                                               NanoXML/Java
 *
 * $Revision: 1.5 $
 * $Date: 2002/03/24 11:37:00 $
 * $Name: RELEASE_2_2_1 $
 *
 * This file is part of NanoXML 2 for Java.
 * Copyright (C) 2000-2002 Marc De Scheemaecker, All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the
 * use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software in
 *     a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source distribution.
 */

package net.n3.nanoxml;


import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;


/**
 * StdXMLParser is the core parser of NanoXML.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.5 $
 */
public final class StdXMLParser {

  /**
   * The builder which creates the logical structure of the XML data.
   */
  private final IXMLBuilder builder;


  /**
   * The reader from which the parser retrieves its data.
   */
  private final StdXMLReader reader;


  /**
   * The entity resolver.
   */
  private final IXMLEntityResolver entityResolver;


  /**
   * The validator that will process entity references and validate the XML
   * data.
   */
  private final IXMLValidator validator;

  /**
   * Creates a new parser.
   */
  public StdXMLParser(StdXMLReader reader,
                      IXMLBuilder builder,
                      IXMLValidator validator,
                      IXMLEntityResolver entityResolver) {
    this.builder = builder;
    this.validator = validator;
    this.reader = reader;
    this.entityResolver = entityResolver;
  }

  /**
   * Returns the builder which creates the logical structure of the XML data.
   *
   * @return the builder
   */
  public IXMLBuilder getBuilder() {
    return builder;
  }


  /**
   * Returns the validator that validates the XML data.
   *
   * @return the validator
   */
  public IXMLValidator getValidator() {
    return validator;
  }


  /**
   * Returns the entity resolver.
   *
   * @return the non-null resolver
   */
  public IXMLEntityResolver getResolver() {
    return entityResolver;
  }


  /**
   * Returns the reader from which the parser retrieves its data.
   *
   * @return the reader
   */
  public StdXMLReader getReader() {
    return reader;
  }


  /**
   * Parses the data and lets the builder create the logical data structure.
   *
   * @return the logical structure built by the builder
   * @throws XMLException if an error occurred reading or parsing the data
   */
  public Object parse() throws XMLException {
    try {
      builder.startBuilding(reader.getSystemID(), reader.getLineNr());
      scanData();
      return builder.getResult();
    }
    catch (XMLException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XMLException(e);
    }
  }


  /**
   * Scans the XML data for elements.
   *
   * @throws Exception if something went wrong
   */
  private void scanData() throws Exception {
    while ((!reader.atEOF()) && (builder.getResult() == null)) {
      String str = XMLUtil.read(reader, '&');
      char ch = str.charAt(0);
      if (ch == '&') {
        XMLUtil.processEntity(str, reader, entityResolver);
        continue;
      }

      switch (ch) {
        case '<':
          scanSomeTag(false, // don't allow CDATA
                           null,  // no default namespace
                           new Properties());
          break;

        case ' ':
        case '\t':
        case '\r':
        case '\n':
          // skip whitespace
          break;

        default:
          XMLUtil.errorInvalidInput(reader.getSystemID(), reader.getLineNr(), "`" + ch + "' (0x" + Integer.toHexString(ch) + ')');
      }
    }
  }


  /**
   * Scans an XML tag.
   *
   * @param allowCDATA       true if CDATA sections are allowed at this point
   * @param defaultNamespace the default namespace URI (or null)
   * @param namespaces       list of defined namespaces
   * @throws Exception if something went wrong
   */
  private void scanSomeTag(boolean allowCDATA, String defaultNamespace, Properties namespaces) throws Exception {
    String str = XMLUtil.read(reader, '&');
    char ch = str.charAt(0);

    if (ch == '&') {
      XMLUtil.errorUnexpectedEntity(reader.getSystemID(), reader.getLineNr(), str);
    }

    switch (ch) {
      case '?':
        processPI();
        break;

      case '!':
        processSpecialTag(allowCDATA);
        break;

      default:
        reader.unread(ch);
        processElement(defaultNamespace, namespaces);
    }
  }


  /**
   * Processes a "processing instruction".
   *
   * @throws Exception if something went wrong
   */
  private void processPI() throws Exception {
    XMLUtil.skipWhitespace(reader, null);
    String target = XMLUtil.scanIdentifier(reader);
    XMLUtil.skipWhitespace(reader, null);
    Reader reader = new PIReader(this.reader);

    if (!target.equalsIgnoreCase("xml")) {
      builder.newProcessingInstruction(target, reader);
    }

    reader.close();
  }


  /**
   * Processes a tag that starts with a bang (&lt;!...&gt;).
   *
   * @param allowCDATA true if CDATA sections are allowed at this point
   * @throws Exception if something went wrong
   */
  private void processSpecialTag(boolean allowCDATA) throws Exception {
    String str = XMLUtil.read(reader, '&');
    char ch = str.charAt(0);

    if (ch == '&') {
      XMLUtil.errorUnexpectedEntity(reader.getSystemID(), reader.getLineNr(), str);
    }

    switch (ch) {
      case '[':
        if (allowCDATA) {
          processCDATA();
        }
        else {
          XMLUtil.errorUnexpectedCDATA(reader.getSystemID(), reader.getLineNr());
        }

        return;

      case 'D':
        processDocType();
        return;

      case '-':
        XMLUtil.skipComment(reader);
    }
  }


  /**
   * Processes a CDATA section.
   *
   * @throws Exception if something went wrong
   */
  private void processCDATA() throws Exception {
    if (!XMLUtil.checkLiteral(reader, "CDATA[")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "<![[CDATA[");
    }

    Reader reader = new CDATAReader(this.reader);
    builder.addPCData(reader, this.reader.getSystemID(), this.reader.getLineNr());
    reader.close();
  }


  /**
   * Processes a document type declaration.
   *
   * @throws Exception if an error occurred reading or parsing the data
   */
  private void processDocType() throws Exception {
    if (!XMLUtil.checkLiteral(reader, "OCTYPE")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "<!DOCTYPE");
      return;
    }

    XMLUtil.skipWhitespace(reader, null);
    String systemID = null;
    StringBuilder publicID = new StringBuilder();
    String rootElement = XMLUtil.scanIdentifier(reader);
    XMLUtil.skipWhitespace(reader, null);
    char ch = reader.read();

    if (ch == 'P') {
      systemID = XMLUtil.scanPublicID(publicID, reader);
      XMLUtil.skipWhitespace(reader, null);
      ch = reader.read();
    }
    else if (ch == 'S') {
      systemID = XMLUtil.scanSystemID(reader);
      XMLUtil.skipWhitespace(reader, null);
      ch = reader.read();
    }

    if (ch == '[') {
      validator.parseDTD(publicID.toString(), reader, entityResolver, false);
      XMLUtil.skipWhitespace(reader, null);
      ch = reader.read();
    }

    if (ch != '>') {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`>'");
    }

    if (systemID != null) {
      Reader reader = this.reader.openStream(publicID.toString(), systemID);
      this.reader.startNewStream(reader);
      this.reader.setSystemID(systemID);
      this.reader.setPublicID(publicID.toString());
      validator.parseDTD(publicID.toString(), this.reader, entityResolver, true);
    }
  }


  /**
   * Processes a regular element.
   *
   * @param defaultNamespace the default namespace URI (or null)
   * @param namespaces       list of defined namespaces
   * @throws Exception if something went wrong
   */
  private void processElement(String defaultNamespace, Properties namespaces) throws Exception {
    String fullName = XMLUtil.scanIdentifier(reader);
    String name = fullName;
    XMLUtil.skipWhitespace(reader, null);
    String prefix = null;
    int colonIndex = name.indexOf(':');

    if (colonIndex > 0) {
      prefix = name.substring(0, colonIndex);
      name = name.substring(colonIndex + 1);
    }

    List<String> attrNames = new ArrayList<>();
    List<String> attrValues = new ArrayList<>();
    List<String> attrTypes = new ArrayList<>();

    validator.elementStarted(fullName, reader.getSystemID(), reader.getLineNr());
    char ch;

    for (; ; ) {
      ch = reader.read();

      if ((ch == '/') || (ch == '>')) {
        break;
      }

      reader.unread(ch);
      processAttribute(attrNames, attrValues, attrTypes);
      XMLUtil.skipWhitespace(reader, null);
    }

    Properties extraAttributes = new Properties();
    validator.elementAttributesProcessed(fullName, extraAttributes, reader.getSystemID(), reader.getLineNr());
    Enumeration<Object> enumeration = extraAttributes.keys();

    while (enumeration.hasMoreElements()) {
      String key = (String)enumeration.nextElement();
      String value = extraAttributes.getProperty(key);
      attrNames.add(key);
      attrValues.add(value);
      attrTypes.add("CDATA");
    }

    for (int i = 0; i < attrNames.size(); i++) {
      String key = attrNames.get(i);
      String value = attrValues.get(i);

      if (key.equals("xmlns")) {
        defaultNamespace = value;
      }
      else if (key.startsWith("xmlns:")) {
        namespaces.setProperty(key.substring(6), value);
      }
    }

    if (prefix == null) {
      builder.startElement(name, prefix, defaultNamespace, reader.getSystemID(), reader.getLineNr());
    }
    else {
      builder.startElement(name, prefix, namespaces.getProperty(prefix), reader.getSystemID(), reader.getLineNr());
    }

    for (int i = 0; i < attrNames.size(); i++) {
      String key = attrNames.get(i);

      if (key.startsWith("xmlns")) {
        continue;
      }

      String value = attrValues.get(i);
      String type = attrTypes.get(i);
      colonIndex = key.indexOf(':');

      if (colonIndex > 0) {
        String attPrefix = key.substring(0, colonIndex);
        key = key.substring(colonIndex + 1);
        builder.addAttribute(key, attPrefix, namespaces.getProperty(attPrefix), value, type);
      }
      else {
        builder.addAttribute(key, null, null, value, type);
      }
    }

    if (prefix == null) {
      builder.elementAttributesProcessed(name, prefix, defaultNamespace);
    }
    else {
      builder.elementAttributesProcessed(name, prefix, namespaces.getProperty(prefix));
    }

    if (ch == '/') {
      if (reader.read() != '>') {
        XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`>'");
      }

      if (prefix == null) {
        builder.endElement(name, prefix, defaultNamespace);
      }
      else {
        builder.endElement(name, prefix, namespaces.getProperty(prefix));
      }

      return;
    }

    StringBuilder buffer = new StringBuilder(16);

    for (; ; ) {
      buffer.setLength(0);
      String str;

      for (; ; ) {
        XMLUtil.skipWhitespace(reader, buffer);
        str = XMLUtil.read(reader, '&');

        if ((str.charAt(0) == '&') && (str.charAt(1) != '#')) {
          XMLUtil.processEntity(str, reader, entityResolver);
        }
        else {
          break;
        }
      }

      if (str.charAt(0) == '<') {
        str = XMLUtil.read(reader, '\0');

        if (str.charAt(0) == '/') {
          XMLUtil.skipWhitespace(reader, null);
          str = XMLUtil.scanIdentifier(reader);

          if (!str.equals(fullName)) {
            XMLUtil.errorWrongClosingTag(reader.getSystemID(), reader.getLineNr(), name, str);
          }

          XMLUtil.skipWhitespace(reader, null);

          if (reader.read() != '>') {
            XMLUtil.errorClosingTagNotEmpty(reader.getSystemID(), reader.getLineNr());
          }

          if (prefix == null) {
            builder.endElement(name, prefix, defaultNamespace);
          }
          else {
            builder.endElement(name, prefix, namespaces.getProperty(prefix));
          }
          break;
        }
        else { // <[^/]
          reader.unread(str.charAt(0));
          scanSomeTag(true, //CDATA allowed
                           defaultNamespace, (Properties)namespaces.clone());
        }
      }
      else { // [^<]
        if (str.charAt(0) == '&') {
          ch = XMLUtil.processCharLiteral(str);
          buffer.append(ch);
        }
        else {
          reader.unread(str.charAt(0));
        }
        Reader r = new ContentReader(reader, entityResolver, buffer.toString());
        builder.addPCData(r, reader.getSystemID(), reader.getLineNr());
        r.close();
      }
    }
  }


  /**
   * Processes an attribute of an element.
   *
   * @param attrNames  contains the names of the attributes.
   * @param attrValues contains the values of the attributes.
   * @param attrTypes  contains the types of the attributes.
   * @throws Exception if something went wrong
   */
  private void processAttribute(List<String> attrNames, List<String> attrValues, List<String> attrTypes) throws Exception {
    String key = XMLUtil.scanIdentifier(reader);
    XMLUtil.skipWhitespace(reader, null);

    if (!XMLUtil.read(reader, '&').equals("=")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`='");
    }

    XMLUtil.skipWhitespace(reader, null);
    String value = XMLUtil.scanString(reader, '&', entityResolver);
    attrNames.add(key);
    attrValues.add(value);
    attrTypes.add("CDATA");
    validator.attributeAdded(key, value, reader.getSystemID(), reader.getLineNr());
  }
}
