/* XMLElement.java                                                 NanoXML/Java
 *
 * $Revision: 1.5 $
 * $Date: 2002/02/06 18:50:12 $
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


import java.io.Serializable;
import java.util.*;


/**
 * XMLElement is an XML element. The standard NanoXML builder generates a
 * tree of such elements.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.5 $
 * @see StdXMLBuilder
 */
public final class XMLElement implements IXMLElement, Serializable {

  /**
   * No line number defined.
   */
  public static final int NO_LINE = -1;
  /**
   * Necessary for serialization.
   */
  private static final long serialVersionUID = -2383376380548624920L;
  /**
   * The attributes of the element.
   */
  private final List<XMLAttribute> attributes;
  /**
   * The child elements.
   */
  private final List<IXMLElement> children;
  /**
   * The system ID of the source data where this element is located.
   */
  private final String systemID;
  /**
   * The line in the source data where this element starts.
   */
  private final int lineNr;
  /**
   * The parent element.
   */
  private IXMLElement parent;
  /**
   * The name of the element.
   */
  private String name;
  /**
   * The full name of the element.
   */
  private String fullName;
  /**
   * The namespace URI.
   */
  private String namespace;
  /**
   * The content of the element.
   */
  private String content;


  /**
   * Creates an empty element to be used for #PCDATA content.
   */
  public XMLElement() {
    this(null, null, null, NO_LINE);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   */
  public XMLElement(String fullName) {
    this(fullName, null, null, NO_LINE);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   * @param systemID the system ID of the XML data where the element starts.
   * @param lineNr   the line in the XML data where the element starts.
   */
  public XMLElement(String fullName, String systemID, int lineNr) {
    this(fullName, null, systemID, lineNr);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   */
  public XMLElement(String fullName, String namespace) {
    this(fullName, namespace, null, NO_LINE);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   * @param systemID  the system ID of the XML data where the element starts.
   * @param lineNr    the line in the XML data where the element starts.
   */
  public XMLElement(String fullName, String namespace, String systemID, int lineNr) {
    attributes = new ArrayList<>();
    children = new ArrayList<>(8);
    this.fullName = fullName;
    if (namespace == null) {
      name = fullName;
    }
    else {
      int index = fullName.indexOf(':');
      if (index >= 0) {
        name = fullName.substring(index + 1);
      }
      else {
        name = fullName;
      }
    }
    this.namespace = namespace;
    content = null;
    this.lineNr = lineNr;
    this.systemID = systemID;
    parent = null;
  }


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   */
  @Override
  public IXMLElement createElement(String fullName) {
    return new XMLElement(fullName);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName the name of the element.
   * @param systemID the system ID of the XML data where the element starts.
   * @param lineNr   the line in the XML data where the element starts.
   */
  @Override
  public IXMLElement createElement(String fullName, String systemID, int lineNr) {
    return new XMLElement(fullName, systemID, lineNr);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   */
  @Override
  public IXMLElement createElement(String fullName, String namespace) {
    return new XMLElement(fullName, namespace);
  }


  /**
   * Creates an empty element.
   *
   * @param fullName  the full name of the element
   * @param namespace the namespace URI.
   * @param systemID  the system ID of the XML data where the element starts.
   * @param lineNr    the line in the XML data where the element starts.
   */
  @Override
  public IXMLElement createElement(String fullName, String namespace, String systemID, int lineNr) {
    return new XMLElement(fullName, namespace, systemID, lineNr);
  }

  /**
   * Returns the parent element. This method returns null for the root
   * element.
   */
  @Override
  public IXMLElement getParent() {
    return parent;
  }


  /**
   * Returns the full name (i.e. the name including an eventual namespace
   * prefix) of the element.
   *
   * @return the name, or null if the element only contains #PCDATA.
   */
  @Override
  public String getFullName() {
    return fullName;
  }


  /**
   * Returns the name of the element.
   *
   * @return the name, or null if the element only contains #PCDATA.
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Sets the full name. This method also sets the short name and clears the
   * namespace URI.
   *
   * @param name the non-null name.
   */
  @Override
  public void setName(String name) {
    this.name = name;
    fullName = name;
    namespace = null;
  }

  /**
   * Returns the namespace of the element.
   *
   * @return the namespace, or null if no namespace is associated with the
   * element.
   */
  @Override
  public String getNamespace() {
    return namespace;
  }

  /**
   * Sets the name.
   *
   * @param fullName  the non-null full name.
   * @param namespace the namespace URI, which may be null.
   */
  @Override
  public void setName(String fullName, String namespace) {
    int index = fullName.indexOf(':');
    if ((namespace == null) || (index < 0)) {
      name = fullName;
    }
    else {
      name = fullName.substring(index + 1);
    }
    this.fullName = fullName;
    this.namespace = namespace;
  }


  /**
   * Adds a child element.
   *
   * @param child the non-null child to add.
   */
  @Override
  public void addChild(IXMLElement child) {
    if (child == null) {
      throw new IllegalArgumentException("child must not be null");
    }
    if ((child.getName() == null) && (!children.isEmpty())) {
      IXMLElement lastChild = children.get(children.size() - 1);

      if (lastChild.getName() == null) {
        lastChild.setContent(lastChild.getContent() + child.getContent());
        return;
      }
    }
    ((XMLElement)child).parent = this;
    children.add(child);
  }


  /**
   * Inserts a child element.
   *
   * @param child the non-null child to add.
   * @param index where to put the child.
   */
  public void insertChild(IXMLElement child, int index) {
    if (child == null) {
      throw new IllegalArgumentException("child must not be null");
    }
    if ((child.getName() == null) && (!children.isEmpty())) {
      IXMLElement lastChild = children.get(children.size() - 1);
      if (lastChild.getName() == null) {
        lastChild.setContent(lastChild.getContent() + child.getContent());
        return;
      }
    }
    ((XMLElement)child).parent = this;
    children.add(index, child);
  }


  /**
   * Removes a child element.
   *
   * @param child the non-null child to remove.
   */
  @Override
  public void removeChild(IXMLElement child) {
    if (child == null) {
      throw new IllegalArgumentException("child must not be null");
    }
    children.remove(child);
  }


  /**
   * Removes the child located at a certain index.
   *
   * @param index the index of the child, where the first child has index 0.
   */
  @Override
  public void removeChildAtIndex(int index) {
    children.remove(index);
  }


  /**
   * Returns whether the element is a leaf element.
   *
   * @return true if the element has no children.
   */
  @Override
  public boolean isLeaf() {
    return children.isEmpty();
  }


  /**
   * Returns whether the element has children.
   *
   * @return true if the element has children.
   */
  @Override
  public boolean hasChildren() {
    return (!children.isEmpty());
  }


  /**
   * Returns the number of children.
   *
   * @return the count.
   */
  @Override
  public int getChildrenCount() {
    return children.size();
  }


  /**
   * Returns a vector containing all the child elements.
   *
   * @return the vector.
   */
  @Override
  public List<IXMLElement> getChildren() {
    return children;
  }


  /**
   * Returns the child at a specific index.
   *
   * @param index the index of the child
   * @return the non-null child
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds.
   */
  @Override
  public IXMLElement getChildAtIndex(int index) throws ArrayIndexOutOfBoundsException {
    return children.get(index);
  }


  /**
   * Searches an attribute.
   *
   * @param fullName the non-null full name of the attribute.
   * @return the attribute, or null if the attribute does not exist.
   */
  private XMLAttribute findAttribute(String fullName) {
    for (XMLAttribute attr : attributes) {
      if (attr.getFullName().equals(fullName)) {
        return attr;
      }
    }
    return null;
  }


  /**
   * Searches an attribute.
   *
   * @param name      the non-null short name of the attribute.
   * @param namespace the name space, which may be null.
   * @return the attribute, or null if the attribute does not exist.
   */
  private XMLAttribute findAttribute(String name, String namespace) {
    for (XMLAttribute attr : attributes) {
      boolean found = attr.getName().equals(name);
      if (namespace == null) {
        found &= (attr.getNamespace() == null);
      }
      else {
        found &= namespace.equals(attr.getNamespace());
      }

      if (found) {
        return attr;
      }
    }
    return null;
  }


  /**
   * Returns the number of attributes.
   */
  @Override
  public int getAttributeCount() {
    return attributes.size();
  }


  /**
   * @param name the non-null name of the attribute.
   * @return the value, or null if the attribute does not exist.
   * @deprecated As of NanoXML/Java 2.1, replaced by
   * {@link #getAttribute(String, String)}
   * Returns the value of an attribute.
   */
  @Override
  @Deprecated
  public String getAttribute(String name) {
    return getAttribute(name, null);
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  @Override
  public String getAttribute(String name, String defaultValue) {
    XMLAttribute attr = findAttribute(name);
    if (attr == null) {
      return defaultValue;
    }
    else {
      return attr.getValue();
    }
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null name of the attribute.
   * @param namespace    the namespace URI, which may be null.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  @Override
  public String getAttribute(String name, String namespace, String defaultValue) {
    XMLAttribute attr = findAttribute(name, namespace);
    if (attr == null) {
      return defaultValue;
    }
    else {
      return attr.getValue();
    }
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  @Override
  public int getAttribute(String name, int defaultValue) {
    String value = getAttribute(name, Integer.toString(defaultValue));
    return Integer.parseInt(value);
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name         the non-null name of the attribute.
   * @param namespace    the namespace URI, which may be null.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  @Override
  public int getAttribute(String name, String namespace, int defaultValue) {
    String value = getAttribute(name, namespace, Integer.toString(defaultValue));
    return Integer.parseInt(value);
  }


  /**
   * Returns the type of attribute.
   *
   * @param name the non-null full name of the attribute.
   * @return the type, or null if the attribute does not exist.
   */
  @Override
  public String getAttributeType(String name) {
    XMLAttribute attr = findAttribute(name);
    if (attr == null) {
      return null;
    }
    else {
      return attr.getType();
    }
  }


  /**
   * Returns the type of attribute.
   *
   * @param name      the non-null name of the attribute.
   * @param namespace the namespace URI, which may be null.
   * @return the type, or null if the attribute does not exist.
   */
  @Override
  public String getAttributeType(String name, String namespace) {
    XMLAttribute attr = findAttribute(name, namespace);
    if (attr == null) {
      return null;
    }
    else {
      return attr.getType();
    }
  }


  /**
   * Sets an attribute.
   *
   * @param name  the non-null full name of the attribute.
   * @param value the non-null value of the attribute.
   */
  @Override
  public void setAttribute(String name, String value) {
    XMLAttribute attr = findAttribute(name);
    if (attr == null) {
      attr = new XMLAttribute(name, name, null, value, "CDATA");
      attributes.add(attr);
    }
    else {
      attr.setValue(value);
    }
  }


  /**
   * Sets an attribute.
   *
   * @param fullName  the non-null full name of the attribute.
   * @param namespace the namespace URI of the attribute, which may be null.
   * @param value     the non-null value of the attribute.
   */
  @Override
  public void setAttribute(String fullName, String namespace, String value) {
    int index = fullName.indexOf(':');
    String name = fullName.substring(index + 1);
    XMLAttribute attr = findAttribute(name, namespace);
    if (attr == null) {
      attr = new XMLAttribute(fullName, name, namespace, value, "CDATA");
      attributes.add(attr);
    }
    else {
      attr.setValue(value);
    }
  }


  /**
   * Removes an attribute.
   *
   * @param name the non-null name of the attribute.
   */
  @Override
  public void removeAttribute(String name) {
    for (int i = 0; i < attributes.size(); i++) {
      XMLAttribute attr = attributes.get(i);
      if (attr.getFullName().equals(name)) {
        attributes.remove(i);
        return;
      }
    }
  }


  /**
   * Removes an attribute.
   *
   * @param name      the non-null name of the attribute.
   * @param namespace the namespace URI of the attribute, which may be null.
   */
  @Override
  public void removeAttribute(String name, String namespace) {
    for (int i = 0; i < attributes.size(); i++) {
      XMLAttribute attr = attributes.get(i);
      boolean found = attr.getName().equals(name);
      if (namespace == null) {
        found &= (attr.getNamespace() == null);
      }
      else {
        found &= attr.getNamespace().equals(namespace);
      }

      if (found) {
        attributes.remove(i);
        return;
      }
    }
  }


  /**
   * Returns whether an attribute exists.
   *
   * @return true if the attribute exists.
   */
  @Override
  public boolean hasAttribute(String name) {
    return findAttribute(name) != null;
  }


  /**
   * Returns whether an attribute exists.
   *
   * @return true if the attribute exists.
   */
  @Override
  public boolean hasAttribute(String name, String namespace) {
    return findAttribute(name, namespace) != null;
  }


  /**
   * Returns all attributes as a Properties object.
   *
   * @return the non-null set.
   */
  @Override
  public Properties getAttributes() {
    Properties result = new Properties();
    for (XMLAttribute attr : attributes) {
      result.setProperty(attr.getFullName(), attr.getValue());
    }
    return result;
  }


  /**
   * Returns the system ID of the data where the element started.
   *
   * @return the system ID, or null if unknown.
   * @see #getLineNr
   */
  @Override
  public String getSystemID() {
    return systemID;
  }


  /**
   * Returns the line number in the data where the element started.
   *
   * @return the line number, or NO_LINE if unknown.
   * @see #NO_LINE
   * @see #getSystemID
   */
  @Override
  public int getLineNr() {
    return lineNr;
  }


  /**
   * Return the #PCDATA content of the element. If the element has a
   * combination of #PCDATA content and child elements, the #PCDATA
   * sections can be retrieved as unnamed child objects. In this case,
   * this method returns null.
   *
   * @return the content.
   */
  @Override
  public String getContent() {
    return content;
  }


  /**
   * Sets the #PCDATA content. It is an error to call this method with a
   * non-null value if there are child objects.
   *
   * @param content the (possibly null) content.
   */
  @Override
  public void setContent(String content) {
    this.content = content;
  }


  /**
   * Returns true if the element equals another element.
   *
   * @param rawElement the element to compare to
   */
  @Override
  public boolean equals(Object rawElement) {
    try {
      return equalsXMLElement((IXMLElement)rawElement);
    }
    catch (ClassCastException e) {
      return false;
    }
  }


  /**
   * Returns true if the element equals another element.
   *
   * @param elt the element to compare to
   */
  @Override
  public boolean equalsXMLElement(IXMLElement elt) {
    if (!name.equals(elt.getName())) {
      return false;
    }
    if (attributes.size() != elt.getAttributeCount()) {
      return false;
    }
    for (XMLAttribute attr : attributes) {
      if (!elt.hasAttribute(attr.getName(), attr.getNamespace())) {
        return false;
      }
      String value = elt.getAttribute(attr.getName(), attr.getNamespace(), null);
      if (!attr.getValue().equals(value)) {
        return false;
      }
      String type = elt.getAttributeType(attr.getName(), attr.getNamespace());
      if (!attr.getType().equals(type)) {
        return false;
      }
    }
    if (children.size() != elt.getChildrenCount()) {
      return false;
    }
    for (int i = 0; i < children.size(); i++) {
      IXMLElement child1 = getChildAtIndex(i);
      IXMLElement child2 = elt.getChildAtIndex(i);

      if (!child1.equalsXMLElement(child2)) {
        return false;
      }
    }
    return true;
  }
}
