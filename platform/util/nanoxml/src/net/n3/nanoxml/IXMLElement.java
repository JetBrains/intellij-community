/* IXMLElement.java                                                NanoXML/Java
 *
 * $Revision: 1.4 $
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
/*
 * This file is an altered version of the original NanoXML source code
 */

package net.n3.nanoxml;


import java.util.List;
import java.util.Properties;


/**
 * IXMLElement is an XML element. It provides an easy to use generic interface
 * on top of an XML like data structure like e.g. a DOM like structure.
 * Elements returned by StdXMLBuilder also implement this interface.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 * @see StdXMLBuilder
 */
public interface IXMLElement {

  /**
   * No line number defined.
   */
  int NO_LINE = -1;


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   */
  IXMLElement createElement(String fullName);


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   * @param systemID the system ID of the XML data where the element starts.
   * @param lineNr   the line in the XML data where the element starts.
   */
  IXMLElement createElement(String fullName, String systemID, int lineNr);


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   */
  IXMLElement createElement(String fullName, String namespace);


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   * @param systemID  the system ID of the XML data where the element starts.
   * @param lineNr    the line in the XML data where the element starts.
   */
  IXMLElement createElement(String fullName, String namespace, String systemID, int lineNr);


  /**
   * Returns the parent element. This method returns null for the root
   * element.
   */
  IXMLElement getParent();


  /**
   * Returns the full name (i.e. the name including an eventual namespace
   * prefix) of the element.
   *
   * @return the name, or null if the element only contains #PCDATA.
   */
  String getFullName();


  /**
   * Returns the name of the element.
   *
   * @return the name, or null if the element only contains #PCDATA.
   */
  String getName();

  /**
   * Sets the full name. This method also sets the short name and clears the
   * namespace URI.
   *
   * @param name the non-null name.
   */
  void setName(String name);

  /**
   * Returns the namespace of the element.
   *
   * @return the namespace, or null if no namespace is associated with the
   * element.
   */
  String getNamespace();

  /**
   * Sets the name.
   *
   * @param fullName  the non-null full name.
   * @param namespace the namespace URI, which may be null.
   */
  void setName(String fullName, String namespace);


  /**
   * Adds a child element.
   *
   * @param child the non-null child to add.
   */
  void addChild(IXMLElement child);


  /**
   * Removes a child element.
   *
   * @param child the non-null child to remove.
   */
  void removeChild(IXMLElement child);


  /**
   * Removes the child located at a certain index.
   *
   * @param index the index of the child, where the first child has index 0.
   */
  void removeChildAtIndex(int index);


  /**
   * Returns whether the element is a leaf element.
   *
   * @return true if the element has no children.
   */
  boolean isLeaf();


  /**
   * Returns whether the element has children.
   *
   * @return true if the element has children.
   */
  boolean hasChildren();


  /**
   * Returns the number of children.
   *
   * @return the count.
   */
  int getChildrenCount();


  /**
   * Returns a vector containing all the child elements.
   *
   * @return the vector.
   */
  List<IXMLElement> getChildren();


  /**
   * Returns the child at a specific index.
   *
   * @param index the index of the child
   * @return the non-null child
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds.
   */
  IXMLElement getChildAtIndex(int index) throws ArrayIndexOutOfBoundsException;


  /**
   * Returns the number of attributes.
   */
  int getAttributeCount();


  /**
   * @param name the non-null name of the attribute.
   * @return the value, or null if the attribute does not exist.
   * @deprecated As of NanoXML/Java 2.0.1, replaced by
   * {@link #getAttribute(String, String)}
   * Returns the value of an attribute.
   */
  @Deprecated
  String getAttribute(String name);


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  String getAttribute(String name, String defaultValue);


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null name of the attribute.
   * @param namespace    the namespace URI, which may be null.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  String getAttribute(String name, String namespace, String defaultValue);


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  int getAttribute(String name, int defaultValue);


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null name of the attribute.
   * @param namespace    the namespace URI, which may be null.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  int getAttribute(String name, String namespace, int defaultValue);


  /**
   * Returns the type of an attribute.
   *
   * @param name the non-null full name of the attribute.
   * @return the type, or null if the attribute does not exist.
   */
  String getAttributeType(String name);


  /**
   * Returns the type of an attribute.
   *
   * @param name      the non-null name of the attribute.
   * @param namespace the namespace URI, which may be null.
   * @return the type, or null if the attribute does not exist.
   */
  String getAttributeType(String name, String namespace);


  /**
   * Sets an attribute.
   *
   * @param name  the non-null full name of the attribute.
   * @param value the non-null value of the attribute.
   */
  void setAttribute(String name, String value);


  /**
   * Sets an attribute.
   *
   * @param fullName  the non-null full name of the attribute.
   * @param namespace the namespace URI of the attribute, which may be null.
   * @param value     the non-null value of the attribute.
   */
  void setAttribute(String fullName, String namespace, String value);


  /**
   * Removes an attribute.
   *
   * @param name the non-null name of the attribute.
   */
  void removeAttribute(String name);


  /**
   * Removes an attribute.
   *
   * @param name      the non-null name of the attribute.
   * @param namespace the namespace URI of the attribute, which may be null.
   */
  void removeAttribute(String name, String namespace);


  /**
   * Returns whether an attribute exists.
   *
   * @param name the non-null name of the attribute.
   * @return true if the attribute exists.
   */
  boolean hasAttribute(String name);


  /**
   * Returns whether an attribute exists.
   *
   * @param name      the non-null name of the attribute.
   * @param namespace the namespace URI of the attribute, which may be null.
   * @return true if the attribute exists.
   */
  boolean hasAttribute(String name, String namespace);


  /**
   * Returns all attributes as a Properties object.
   *
   * @return the non-null set.
   */
  Properties getAttributes();


  /**
   * Returns the system ID of the data where the element started.
   *
   * @return the system ID, or null if unknown.
   * @see #getLineNr
   */
  String getSystemID();


  /**
   * Returns the line number in the data where the element started.
   *
   * @return the line number, or NO_LINE if unknown.
   * @see #NO_LINE
   * @see #getSystemID
   */
  int getLineNr();


  /**
   * Return the #PCDATA content of the element. If the element has a
   * combination of #PCDATA content and child elements, the #PCDATA
   * sections can be retrieved as unnamed child objects. In this case,
   * this method returns null.
   *
   * @return the content.
   */
  String getContent();


  /**
   * Sets the #PCDATA content. It is an error to call this method with a
   * non-null value if there are child objects.
   *
   * @param content the (possibly null) content.
   */
  void setContent(String content);


  /**
   * Returns true if the element equals another element.
   *
   * @param rawElement the element to compare to
   */
  @Override
  boolean equals(Object rawElement);


  /**
   * Returns true if the element equals another element.
   */
  boolean equalsXMLElement(IXMLElement elt);
}
