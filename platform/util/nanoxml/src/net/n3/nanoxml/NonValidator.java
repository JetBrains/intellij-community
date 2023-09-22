/* NonValidator.java                                               NanoXML/Java
 *
 * $Revision: 1.4 $
 * $Date: 2002/02/03 21:19:38 $
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
import java.io.StringReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Stack;


/**
 * NonValidator is a concrete implementation of IXMLValidator which processes
 * the DTD and handles entity definitions. It does not do any validation
 * itself.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 */
public class NonValidator
  implements IXMLValidator {

  /**
   * The parameter entity resolver.
   */
  protected IXMLEntityResolver parameterEntityResolver;


  /**
   * Contains the default values for attributes for the different element
   * types.
   */
  protected Hashtable attributeDefaultValues;


  /**
   * The stack of elements to be processed.
   */
  protected Stack currentElements;


  /**
   * Creates the &quot;validator&quot;.
   */
  public NonValidator() {
    this.attributeDefaultValues = new Hashtable();
    this.currentElements = new Stack();
    this.parameterEntityResolver = new XMLEntityResolver();
  }


  /**
   * Cleans up the object when it's destroyed.
   */
  @Override
  protected void finalize()
    throws Throwable {
    this.parameterEntityResolver = null;
    this.attributeDefaultValues.clear();
    this.attributeDefaultValues = null;
    this.currentElements.clear();
    this.currentElements = null;
    super.finalize();
  }

  /**
   * Returns the parameter entity resolver.
   *
   * @return the entity resolver.
   */
  @Override
  public IXMLEntityResolver getParameterEntityResolver() {
    return this.parameterEntityResolver;
  }

  /**
   * Sets the parameter entity resolver.
   *
   * @param resolver the entity resolver.
   */
  @Override
  public void setParameterEntityResolver(IXMLEntityResolver resolver) {
    this.parameterEntityResolver = resolver;
  }

  /**
   * Parses the DTD. The validator object is responsible for reading the
   * full DTD.
   *
   * @param publicID       the public ID, which may be null.
   * @param reader         the reader to read the DTD from.
   * @param entityResolver the entity resolver.
   * @param external       true if the DTD is external.
   * @throws Exception If something went wrong.
   */
  @Override
  public void parseDTD(String publicID,
                       IXMLReader reader,
                       IXMLEntityResolver entityResolver,
                       boolean external)
    throws Exception {
    XMLUtil.skipWhitespace(reader, null);
    int origLevel = reader.getStreamLevel();

    for (; ; ) {
      String str = XMLUtil.read(reader, '%');
      char ch = str.charAt(0);

      if (ch == '%') {
        XMLUtil.processEntity(str, reader,
                              this.parameterEntityResolver);
        continue;
      }
      else if (ch == '<') {
        this.processElement(reader, entityResolver);
      }
      else if (ch == ']') {
        return; // end internal DTD
      }
      else {
        XMLUtil.errorInvalidInput(reader.getSystemID(),
                                  reader.getLineNr(),
                                  str);
      }

      do {
        ch = reader.read();

        if (external && (reader.getStreamLevel() < origLevel)) {
          reader.unread(ch);
          return; // end external DTD
        }
      }
      while ((ch == ' ') || (ch == '\t') || (ch == '\n')
             || (ch == '\r'));

      reader.unread(ch);
    }
  }


  /**
   * Processes an element in the DTD.
   *
   * @param reader         the reader to read data from.
   * @param entityResolver the entity resolver.
   * @throws Exception If something went wrong.
   */
  protected void processElement(IXMLReader reader,
                                IXMLEntityResolver entityResolver)
    throws Exception {
    String str = XMLUtil.read(reader, '%');
    char ch = str.charAt(0);

    if (ch != '!') {
      XMLUtil.skipTag(reader);
      return;
    }

    str = XMLUtil.read(reader, '%');
    ch = str.charAt(0);

    switch (ch) {
      case '-':
        XMLUtil.skipComment(reader);
        break;

      case '[':
        this.processConditionalSection(reader, entityResolver);
        break;

      case 'E':
        this.processEntity(reader, entityResolver);
        break;

      case 'A':
        this.processAttList(reader, entityResolver);
        break;

      default:
        XMLUtil.skipTag(reader);
    }
  }


  /**
   * Processes a conditional section.
   *
   * @param reader         the reader to read data from.
   * @param entityResolver the entity resolver.
   * @throws Exception If something went wrong.
   */
  protected void processConditionalSection(IXMLReader reader,
                                           IXMLEntityResolver entityResolver)
    throws Exception {
    XMLUtil.skipWhitespace(reader, null);

    String str = XMLUtil.read(reader, '%');
    char ch = str.charAt(0);

    if (ch != 'I') {
      XMLUtil.skipTag(reader);
      return;
    }

    str = XMLUtil.read(reader, '%');
    ch = str.charAt(0);

    switch (ch) {
      case 'G':
        this.processIgnoreSection(reader, entityResolver);
        return;

      case 'N':
        break;

      default:
        XMLUtil.skipTag(reader);
        return;
    }

    if (!XMLUtil.checkLiteral(reader, "CLUDE")) {
      XMLUtil.skipTag(reader);
      return;
    }

    XMLUtil.skipWhitespace(reader, null);

    str = XMLUtil.read(reader, '%');
    ch = str.charAt(0);

    if (ch != '[') {
      XMLUtil.skipTag(reader);
      return;
    }

    Reader subreader = new CDATAReader(reader);
    StringBuffer buf = new StringBuffer(1024);

    for (; ; ) {
      int ch2 = subreader.read();

      if (ch2 < 0) {
        break;
      }

      buf.append((char)ch2);
    }

    subreader.close();
    reader.startNewStream(new StringReader(buf.toString()));
  }


  /**
   * Processes an ignore section.
   *
   * @param reader         the reader to read data from.
   * @param entityResolver the entity resolver.
   * @throws Exception If something went wrong.
   */
  protected void processIgnoreSection(IXMLReader reader,
                                      IXMLEntityResolver entityResolver)
    throws Exception {
    if (!XMLUtil.checkLiteral(reader, "NORE")) {
      XMLUtil.skipTag(reader);
      return;
    }

    XMLUtil.skipWhitespace(reader, null);

    String str = XMLUtil.read(reader, '%');
    char ch = str.charAt(0);

    if (ch != '[') {
      XMLUtil.skipTag(reader);
      return;
    }

    Reader subreader = new CDATAReader(reader);
    subreader.close();
  }


  /**
   * Processes an ATTLIST element.
   *
   * @param reader         the reader to read data from.
   * @param entityResolver the entity resolver.
   * @throws Exception If something went wrong.
   */
  protected void processAttList(IXMLReader reader,
                                IXMLEntityResolver entityResolver)
    throws Exception {
    if (!XMLUtil.checkLiteral(reader, "TTLIST")) {
      XMLUtil.skipTag(reader);
      return;
    }

    XMLUtil.skipWhitespace(reader, null);
    String str = XMLUtil.read(reader, '%');
    char ch = str.charAt(0);
    while (ch == '%') {
      XMLUtil.processEntity(str, reader,
                            this.parameterEntityResolver);
      str = XMLUtil.read(reader, '%');
      ch = str.charAt(0);
    }
    reader.unread(ch);
    String elementName = XMLUtil.scanIdentifier(reader);
    XMLUtil.skipWhitespace(reader, null);

    str = XMLUtil.read(reader, '%');
    ch = str.charAt(0);
    while (ch == '%') {
      XMLUtil.processEntity(str, reader,
                            this.parameterEntityResolver);
      str = XMLUtil.read(reader, '%');
      ch = str.charAt(0);
    }

    Properties props = new Properties();

    while (ch != '>') {
      reader.unread(ch);
      String attName = XMLUtil.scanIdentifier(reader);
      XMLUtil.skipWhitespace(reader, null);
      str = XMLUtil.read(reader, '%');
      ch = str.charAt(0);
      while (ch == '%') {
        XMLUtil.processEntity(str, reader,
                              this.parameterEntityResolver);
        str = XMLUtil.read(reader, '%');
        ch = str.charAt(0);
      }

      if (ch == '(') {
        while (ch != ')') {
          str = XMLUtil.read(reader, '%');
          ch = str.charAt(0);
          while (ch == '%') {
            XMLUtil.processEntity(str, reader,
                                  this.parameterEntityResolver);
            str = XMLUtil.read(reader, '%');
            ch = str.charAt(0);
          }
        }
      }
      else {
        reader.unread(ch);
        XMLUtil.scanIdentifier(reader);
      }

      XMLUtil.skipWhitespace(reader, null);
      str = XMLUtil.read(reader, '%');
      ch = str.charAt(0);
      while (ch == '%') {
        XMLUtil.processEntity(str, reader,
                              this.parameterEntityResolver);
        str = XMLUtil.read(reader, '%');
        ch = str.charAt(0);
      }

      if (ch == '#') {
        str = XMLUtil.scanIdentifier(reader);
        XMLUtil.skipWhitespace(reader, null);

        if (!str.equals("FIXED")) {
          XMLUtil.skipWhitespace(reader, null);

          str = XMLUtil.read(reader, '%');
          ch = str.charAt(0);
          while (ch == '%') {
            XMLUtil.processEntity(str, reader,
                                  this.parameterEntityResolver);
            str = XMLUtil.read(reader, '%');
            ch = str.charAt(0);
          }

          continue;
        }
      }
      else {
        reader.unread(ch);
      }

      String value = XMLUtil.scanString(reader, '%',
                                        this.parameterEntityResolver);
      props.setProperty(attName, value);
      XMLUtil.skipWhitespace(reader, null);

      str = XMLUtil.read(reader, '%');
      ch = str.charAt(0);
      while (ch == '%') {
        XMLUtil.processEntity(str, reader,
                              this.parameterEntityResolver);
        str = XMLUtil.read(reader, '%');
        ch = str.charAt(0);
      }
    }

    if (!props.isEmpty()) {
      this.attributeDefaultValues.put(elementName, props);
    }
  }


  /**
   * Processes an ENTITY element.
   *
   * @param reader         the reader to read data from.
   * @param entityResolver the entity resolver.
   * @throws Exception If something went wrong.
   */
  protected void processEntity(IXMLReader reader,
                               IXMLEntityResolver entityResolver)
    throws Exception {
    if (!XMLUtil.checkLiteral(reader, "NTITY")) {
      XMLUtil.skipTag(reader);
      return;
    }

    XMLUtil.skipWhitespace(reader, null);
    char ch = XMLUtil.readChar(reader, '\0');

    if (ch == '%') {
      XMLUtil.skipWhitespace(reader, null);
      entityResolver = this.parameterEntityResolver;
    }
    else {
      reader.unread(ch);
    }

    String key = XMLUtil.scanIdentifier(reader);
    XMLUtil.skipWhitespace(reader, null);
    ch = XMLUtil.readChar(reader, '%');
    String systemID = null;
    String publicID = null;

    switch (ch) {
      case 'P':
        if (!XMLUtil.checkLiteral(reader, "UBLIC")) {
          XMLUtil.skipTag(reader);
          return;
        }

        XMLUtil.skipWhitespace(reader, null);
        publicID = XMLUtil.scanString(reader, '%',
                                      this.parameterEntityResolver);
        XMLUtil.skipWhitespace(reader, null);
        systemID = XMLUtil.scanString(reader, '%',
                                      this.parameterEntityResolver);
        XMLUtil.skipWhitespace(reader, null);
        XMLUtil.readChar(reader, '%');
        break;

      case 'S':
        if (!XMLUtil.checkLiteral(reader, "YSTEM")) {
          XMLUtil.skipTag(reader);
          return;
        }

        XMLUtil.skipWhitespace(reader, null);
        systemID = XMLUtil.scanString(reader, '%',
                                      this.parameterEntityResolver);
        XMLUtil.skipWhitespace(reader, null);
        XMLUtil.readChar(reader, '%');
        break;

      case '"':
      case '\'':
        reader.unread(ch);
        String value = XMLUtil.scanString(reader, '%',
                                          this.parameterEntityResolver);
        entityResolver.addInternalEntity(key, value);
        XMLUtil.skipWhitespace(reader, null);
        XMLUtil.readChar(reader, '%');
        break;

      default:
        XMLUtil.skipTag(reader);
    }

    if (systemID != null) {
      entityResolver.addExternalEntity(key, publicID, systemID);
    }
  }


  /**
   * Indicates that an element has been started.
   *
   * @param name     the name of the element.
   * @param systemId the system ID of the XML data of the element.
   * @param lineNr   the line number in the XML data of the element.
   */
  @Override
  public void elementStarted(String name,
                             String systemId,
                             int lineNr) {
    Properties attribs
      = (Properties)this.attributeDefaultValues.get(name);

    if (attribs == null) {
      attribs = new Properties();
    }
    else {
      attribs = (Properties)attribs.clone();
    }

    this.currentElements.push(attribs);
  }


  /**
   * Indicates that the current element has ended.
   *
   * @param name     the name of the element.
   * @param systemId the system ID of the XML data of the element.
   * @param lineNr   the line number in the XML data of the element.
   */
  @Override
  public void elementEnded(String name,
                           String systemId,
                           int lineNr) {
    // nothing to do
  }


  /**
   * This method is called when the attributes of an XML element have been
   * processed.
   * If there are attributes with a default value which have not been
   * specified yet, they have to be put into <I>extraAttributes</I>.
   *
   * @param name            the name of the element.
   * @param extraAttributes where to put extra attributes.
   * @param systemId        the system ID of the XML data of the element.
   * @param lineNr          the line number in the XML data of the element.
   */
  @Override
  public void elementAttributesProcessed(String name,
                                         Properties extraAttributes,
                                         String systemId,
                                         int lineNr) {
    Properties props = (Properties)this.currentElements.pop();
    Enumeration enumeration = props.keys();

    while (enumeration.hasMoreElements()) {
      String key = (String)enumeration.nextElement();
      extraAttributes.put(key, props.get(key));
    }
  }


  /**
   * Indicates that an attribute has been added to the current element.
   *
   * @param key      the name of the attribute.
   * @param value    the value of the attribute.
   * @param systemId the system ID of the XML data of the element.
   * @param lineNr   the line number in the XML data of the element.
   */
  @Override
  public void attributeAdded(String key,
                             String value,
                             String systemId,
                             int lineNr) {
    Properties props = (Properties)this.currentElements.peek();

    props.remove(key);
  }


  /**
   * Indicates that a new #PCDATA element has been encountered.
   *
   * @param systemId the system ID of the XML data of the element.
   * @param lineNr   the line number in the XML data of the element.
   */
  @Override
  public void PCDataAdded(String systemId,
                          int lineNr) {
    // nothing to do
  }
}
