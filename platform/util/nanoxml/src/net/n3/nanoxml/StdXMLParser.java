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
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;


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
  private IXMLBuilder builder;


  /**
   * The reader from which the parser retrieves its data.
   */
  private IXMLReader reader;


  /**
   * The entity resolver.
   */
  private IXMLEntityResolver entityResolver;


  /**
   * The validator that will process entity references and validate the XML
   * data.
   */
  private IXMLValidator validator;


  /**
   * Creates a new parser.
   */
  public StdXMLParser() {
    this.builder = null;
    this.validator = null;
    this.reader = null;
    this.entityResolver = new XMLEntityResolver();
  }


  /**
   * Cleans up the object when it's destroyed.
   */
  @Override
  protected void finalize() throws Throwable {
    this.builder = null;
    this.reader = null;
    this.entityResolver = null;
    this.validator = null;
    super.finalize();
  }

  /**
   * Returns the builder which creates the logical structure of the XML data.
   *
   * @return the builder
   */
  public IXMLBuilder getBuilder() {
    return this.builder;
  }

  /**
   * Sets the builder which creates the logical structure of the XML data.
   *
   * @param builder the non-null builder
   */
  public void setBuilder(IXMLBuilder builder) {
    this.builder = builder;
  }

  /**
   * Returns the validator that validates the XML data.
   *
   * @return the validator
   */
  public IXMLValidator getValidator() {
    return this.validator;
  }

  /**
   * Sets the validator that validates the XML data.
   *
   * @param validator the non-null validator
   */
  public void setValidator(IXMLValidator validator) {
    this.validator = validator;
  }

  /**
   * Returns the entity resolver.
   *
   * @return the non-null resolver
   */
  public IXMLEntityResolver getResolver() {
    return this.entityResolver;
  }

  /**
   * Sets the entity resolver.
   *
   * @param resolver the non-null resolver
   */
  public void setResolver(IXMLEntityResolver resolver) {
    this.entityResolver = resolver;
  }

  /**
   * Returns the reader from which the parser retrieves its data.
   *
   * @return the reader
   */
  public IXMLReader getReader() {
    return this.reader;
  }

  /**
   * Sets the reader from which the parser retrieves its data.
   *
   * @param reader the reader
   */
  public void setReader(IXMLReader reader) {
    this.reader = reader;
  }

  /**
   * Parses the data and lets the builder create the logical data structure.
   *
   * @return the logical structure built by the builder
   * @throws XMLException if an error occurred reading or parsing the data
   */
  public Object parse() throws XMLException {
    try {
      this.builder.startBuilding(this.reader.getSystemID(), this.reader.getLineNr());
      this.scanData();
      return this.builder.getResult();
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
    while ((!this.reader.atEOF()) && (this.builder.getResult() == null)) {
      String str = XMLUtil.read(this.reader, '&');
      char ch = str.charAt(0);
      if (ch == '&') {
        XMLUtil.processEntity(str, this.reader, this.entityResolver);
        continue;
      }

      switch (ch) {
        case '<':
          this.scanSomeTag(false, // don't allow CDATA
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
    String str = XMLUtil.read(this.reader, '&');
    char ch = str.charAt(0);

    if (ch == '&') {
      XMLUtil.errorUnexpectedEntity(reader.getSystemID(), reader.getLineNr(), str);
    }

    switch (ch) {
      case '?':
        this.processPI();
        break;

      case '!':
        this.processSpecialTag(allowCDATA);
        break;

      default:
        this.reader.unread(ch);
        this.processElement(defaultNamespace, namespaces);
    }
  }


  /**
   * Processes a "processing instruction".
   *
   * @throws Exception if something went wrong
   */
  private void processPI() throws Exception {
    XMLUtil.skipWhitespace(this.reader, null);
    String target = XMLUtil.scanIdentifier(this.reader);
    XMLUtil.skipWhitespace(this.reader, null);
    Reader reader = new PIReader(this.reader);

    if (!target.equalsIgnoreCase("xml")) {
      this.builder.newProcessingInstruction(target, reader);
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
    String str = XMLUtil.read(this.reader, '&');
    char ch = str.charAt(0);

    if (ch == '&') {
      XMLUtil.errorUnexpectedEntity(reader.getSystemID(), reader.getLineNr(), str);
    }

    switch (ch) {
      case '[':
        if (allowCDATA) {
          this.processCDATA();
        }
        else {
          XMLUtil.errorUnexpectedCDATA(reader.getSystemID(), reader.getLineNr());
        }

        return;

      case 'D':
        this.processDocType();
        return;

      case '-':
        XMLUtil.skipComment(this.reader);
    }
  }


  /**
   * Processes a CDATA section.
   *
   * @throws Exception if something went wrong
   */
  private void processCDATA() throws Exception {
    if (!XMLUtil.checkLiteral(this.reader, "CDATA[")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "<![[CDATA[");
    }

    this.validator.PCDataAdded(this.reader.getSystemID(), this.reader.getLineNr());
    Reader reader = new CDATAReader(this.reader);
    this.builder.addPCData(reader, this.reader.getSystemID(), this.reader.getLineNr());
    reader.close();
  }


  /**
   * Processes a document type declaration.
   *
   * @throws Exception if an error occurred reading or parsing the data
   */
  private void processDocType() throws Exception {
    if (!XMLUtil.checkLiteral(this.reader, "OCTYPE")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "<!DOCTYPE");
      return;
    }

    XMLUtil.skipWhitespace(this.reader, null);
    String systemID = null;
    StringBuffer publicID = new StringBuffer();
    String rootElement = XMLUtil.scanIdentifier(this.reader);
    XMLUtil.skipWhitespace(this.reader, null);
    char ch = this.reader.read();

    if (ch == 'P') {
      systemID = XMLUtil.scanPublicID(publicID, reader);
      XMLUtil.skipWhitespace(this.reader, null);
      ch = this.reader.read();
    }
    else if (ch == 'S') {
      systemID = XMLUtil.scanSystemID(reader);
      XMLUtil.skipWhitespace(this.reader, null);
      ch = this.reader.read();
    }

    if (ch == '[') {
      this.validator.parseDTD(publicID.toString(), this.reader, this.entityResolver, false);
      XMLUtil.skipWhitespace(this.reader, null);
      ch = this.reader.read();
    }

    if (ch != '>') {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`>'");
    }

    if (systemID != null) {
      Reader reader = this.reader.openStream(publicID.toString(), systemID);
      this.reader.startNewStream(reader);
      this.reader.setSystemID(systemID);
      this.reader.setPublicID(publicID.toString());
      this.validator.parseDTD(publicID.toString(), this.reader, this.entityResolver, true);
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
    String fullName = XMLUtil.scanIdentifier(this.reader);
    String name = fullName;
    XMLUtil.skipWhitespace(this.reader, null);
    String prefix = null;
    int colonIndex = name.indexOf(':');

    if (colonIndex > 0) {
      prefix = name.substring(0, colonIndex);
      name = name.substring(colonIndex + 1);
    }

    Vector attrNames = new Vector();
    Vector attrValues = new Vector();
    Vector attrTypes = new Vector();

    this.validator.elementStarted(fullName, this.reader.getSystemID(), this.reader.getLineNr());
    char ch;

    for (; ; ) {
      ch = this.reader.read();

      if ((ch == '/') || (ch == '>')) {
        break;
      }

      this.reader.unread(ch);
      this.processAttribute(attrNames, attrValues, attrTypes);
      XMLUtil.skipWhitespace(this.reader, null);
    }

    Properties extraAttributes = new Properties();
    this.validator.elementAttributesProcessed(fullName, extraAttributes, this.reader.getSystemID(), this.reader.getLineNr());
    Enumeration enumeration = extraAttributes.keys();

    while (enumeration.hasMoreElements()) {
      String key = (String)enumeration.nextElement();
      String value = extraAttributes.getProperty(key);
      attrNames.addElement(key);
      attrValues.addElement(value);
      attrTypes.addElement("CDATA");
    }

    for (int i = 0; i < attrNames.size(); i++) {
      String key = (String)attrNames.elementAt(i);
      String value = (String)attrValues.elementAt(i);
      String type = (String)attrTypes.elementAt(i);

      if (key.equals("xmlns")) {
        defaultNamespace = value;
      }
      else if (key.startsWith("xmlns:")) {
        namespaces.setProperty(key.substring(6), value);
      }
    }

    if (prefix == null) {
      this.builder.startElement(name, prefix, defaultNamespace, this.reader.getSystemID(), this.reader.getLineNr());
    }
    else {
      this.builder.startElement(name, prefix, namespaces.getProperty(prefix), this.reader.getSystemID(), this.reader.getLineNr());
    }

    for (int i = 0; i < attrNames.size(); i++) {
      String key = (String)attrNames.elementAt(i);

      if (key.startsWith("xmlns")) {
        continue;
      }

      String value = (String)attrValues.elementAt(i);
      String type = (String)attrTypes.elementAt(i);
      colonIndex = key.indexOf(':');

      if (colonIndex > 0) {
        String attPrefix = key.substring(0, colonIndex);
        key = key.substring(colonIndex + 1);
        this.builder.addAttribute(key, attPrefix, namespaces.getProperty(attPrefix), value, type);
      }
      else {
        this.builder.addAttribute(key, null, null, value, type);
      }
    }

    if (prefix == null) {
      this.builder.elementAttributesProcessed(name, prefix, defaultNamespace);
    }
    else {
      this.builder.elementAttributesProcessed(name, prefix, namespaces.getProperty(prefix));
    }

    if (ch == '/') {
      if (this.reader.read() != '>') {
        XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`>'");
      }

      this.validator.elementEnded(name, this.reader.getSystemID(), this.reader.getLineNr());

      if (prefix == null) {
        this.builder.endElement(name, prefix, defaultNamespace);
      }
      else {
        this.builder.endElement(name, prefix, namespaces.getProperty(prefix));
      }

      return;
    }

    StringBuffer buffer = new StringBuffer(16);

    for (; ; ) {
      buffer.setLength(0);
      String str;

      for (; ; ) {
        XMLUtil.skipWhitespace(this.reader, buffer);
        str = XMLUtil.read(this.reader, '&');

        if ((str.charAt(0) == '&') && (str.charAt(1) != '#')) {
          XMLUtil.processEntity(str, this.reader, this.entityResolver);
        }
        else {
          break;
        }
      }

      if (str.charAt(0) == '<') {
        str = XMLUtil.read(this.reader, '\0');

        if (str.charAt(0) == '/') {
          XMLUtil.skipWhitespace(this.reader, null);
          str = XMLUtil.scanIdentifier(this.reader);

          if (!str.equals(fullName)) {
            XMLUtil.errorWrongClosingTag(reader.getSystemID(), reader.getLineNr(), name, str);
          }

          XMLUtil.skipWhitespace(this.reader, null);

          if (this.reader.read() != '>') {
            XMLUtil.errorClosingTagNotEmpty(reader.getSystemID(), reader.getLineNr());
          }

          this.validator.elementEnded(fullName, this.reader.getSystemID(), this.reader.getLineNr());
          if (prefix == null) {
            this.builder.endElement(name, prefix, defaultNamespace);
          }
          else {
            this.builder.endElement(name, prefix, namespaces.getProperty(prefix));
          }
          break;
        }
        else { // <[^/]
          this.reader.unread(str.charAt(0));
          this.scanSomeTag(true, //CDATA allowed
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
        this.validator.PCDataAdded(this.reader.getSystemID(), this.reader.getLineNr());
        Reader r = new ContentReader(this.reader, this.entityResolver, buffer.toString());
        this.builder.addPCData(r, this.reader.getSystemID(), this.reader.getLineNr());
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
  private void processAttribute(Vector attrNames, Vector attrValues, Vector attrTypes) throws Exception {
    String key = XMLUtil.scanIdentifier(this.reader);
    XMLUtil.skipWhitespace(this.reader, null);

    if (!XMLUtil.read(this.reader, '&').equals("=")) {
      XMLUtil.errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "`='");
    }

    XMLUtil.skipWhitespace(this.reader, null);
    String value = XMLUtil.scanString(this.reader, '&', this.entityResolver);
    attrNames.addElement(key);
    attrValues.addElement(value);
    attrTypes.addElement("CDATA");
    this.validator.attributeAdded(key, value, this.reader.getSystemID(), this.reader.getLineNr());
  }
}
