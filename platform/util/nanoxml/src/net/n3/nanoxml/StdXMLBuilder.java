/* StdXMLBuilder.java                                              NanoXML/Java
 *
 * $Revision: 1.3 $
 * $Date: 2002/01/04 21:03:28 $
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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * StdXMLBuilder is a concrete implementation of IXMLBuilder which creates a
 * tree of IXMLElement from an XML data source.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 * @see XMLElement
 */
public final class StdXMLBuilder implements IXMLBuilder {
  /**
   * Prototype element for creating the tree.
   */
  private final IXMLElement prototype;

  /**
   * This stack contains the current element and its parents.
   */
  private Deque<IXMLElement> stack;

  /**
   * The root element of the parsed XML tree.
   */
  private IXMLElement root;

  /**
   * Creates the builder.
   */
  public StdXMLBuilder() {
    this(new XMLElement());
  }

  /**
   * Creates the builder.
   *
   * @param prototype the prototype to use when building the tree.
   */
  public StdXMLBuilder(IXMLElement prototype) {
    this.prototype = prototype;
  }

  /**
   * This method is called before the parser starts processing its input.
   *
   * @param systemID the system ID of the XML data source.
   * @param lineNr   the line on which the parsing starts.
   */
  @Override
  public void startBuilding(String systemID, int lineNr) {
    stack = new ArrayDeque<>();
  }

  /**
   * This method is called when a processing instruction is encountered.
   * PIs with target "xml" are handled by the parser.
   *
   * @param target the PI target.
   * @param reader to read the data from the PI.
   */
  @Override
  public void newProcessingInstruction(String target, Reader reader) {
    // nothing to do
  }

  /**
   * This method is called when a new XML element is encountered.
   *
   * @param name     the name of the element.
   * @param nsPrefix the prefix used to identify the namespace. If no
   *                 namespace has been specified, this parameter is null.
   * @param nsURI    the URI associated with the namespace. If no
   *                 namespace has been specified, or no URI is
   *                 associated with nsPrefix, this parameter is null.
   * @param systemID the system ID of the XML data source.
   * @param lineNr   the line in the source where the element starts.
   * @see #endElement
   */
  @Override
  public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
    String fullName = name;
    if (nsPrefix != null) {
      fullName = nsPrefix + ':' + name;
    }
    IXMLElement elt = prototype.createElement(fullName, nsURI, systemID, lineNr);
    if (stack.isEmpty()) {
      root = elt;
    }
    else {
      IXMLElement top = stack.peek();
      top.addChild(elt);
    }
    stack.push(elt);
  }

  /**
   * This method is called when the attributes of an XML element have been
   * processed.
   *
   * @param name     the name of the element.
   * @param nsPrefix the prefix used to identify the namespace. If no
   *                 namespace has been specified, this parameter is null.
   * @param nsURI    the URI associated with the namespace. If no
   *                 namespace has been specified, or no URI is
   *                 associated with nsPrefix, this parameter is null.
   * @see #startElement
   * @see #addAttribute
   */
  @Override
  public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) {
    // nothing to do
  }

  /**
   * This method is called when the end of an XML elemnt is encountered.
   *
   * @param name     the name of the element.
   * @param nsPrefix the prefix used to identify the namespace. If no
   *                 namespace has been specified, this parameter is null.
   * @param nsURI    the URI associated with the namespace. If no
   *                 namespace has been specified, or no URI is
   *                 associated with nsPrefix, this parameter is null.
   * @see #startElement
   */
  @Override
  public void endElement(String name, String nsPrefix, String nsURI) {
    IXMLElement elt = stack.pop();

    if (elt.getChildrenCount() == 1) {
      IXMLElement child = elt.getChildAtIndex(0);
      if (child.getName() == null) {
        elt.setContent(child.getContent());
        elt.removeChildAtIndex(0);
      }
    }
  }

  /**
   * This method is called when a new attribute of an XML element is
   * encountered.
   *
   * @param key      the key (name) of the attribute.
   * @param nsPrefix the prefix used to identify the namespace. If no
   *                 namespace has been specified, this parameter is null.
   * @param nsURI    the URI associated with the namespace. If no
   *                 namespace has been specified, or no URI is
   *                 associated with nsPrefix, this parameter is null.
   * @param value    the value of the attribute.
   * @param type     the type of the attribute. If no type is known,
   *                 "CDATA" is returned.
   * @throws Exception If an exception occurred while processing the event.
   */
  @Override
  public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
    String fullName = key;
    if (nsPrefix != null) {
      fullName = nsPrefix + ':' + key;
    }
    IXMLElement top = stack.peek();
    if (top.hasAttribute(fullName)) {
      throw new XMLParseException(top.getSystemID(), top.getLineNr(), "Duplicate attribute: " + key);
    }
    if (nsPrefix != null) {
      top.setAttribute(fullName, nsURI, value);
    }
    else {
      top.setAttribute(fullName, value);
    }
  }

  /**
   * This method is called when a PCDATA element is encountered. A Java
   * reader is supplied from which you can read the data. The reader will
   * only read the data of the element. You don't need to check for
   * boundaries. If you don't read the full element, the rest of the data
   * is skipped. You also don't have to care about entities; they are
   * resolved by the parser.
   *
   * @param reader   the Java reader from which you can retrieve the data.
   * @param systemID the system ID of the XML data source.
   * @param lineNr   the line in the source where the element starts.
   */
  @Override
  public void addPCData(Reader reader, String systemID, int lineNr) {
    int bufSize = 2048;
    int sizeRead = 0;
    StringBuilder str = new StringBuilder(bufSize);
    char[] buf = new char[bufSize];
    for (; ; ) {
      if (sizeRead >= bufSize) {
        bufSize *= 2;
        str.ensureCapacity(bufSize);
      }
      int size;
      try {
        size = reader.read(buf);
      }
      catch (IOException e) {
        break;
      }
      if (size < 0) {
        break;
      }
      str.append(buf, 0, size);
      sizeRead += size;
    }
    IXMLElement elt = prototype.createElement(null, systemID, lineNr);
    elt.setContent(str.toString());
    if (!stack.isEmpty()) {
      IXMLElement top = stack.peek();
      top.addChild(elt);
    }
  }

  /**
   * Returns the result of the building process. This method is called just
   * before the <I>parse</I> method of IXMLParser returns.
   *
   * @return the result of the building process.
   */
  @Override
  public Object getResult() {
    return root;
  }
}
