/*--

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * An XML attribute. Methods allow the user to obtain the value of the attribute
 * as well as namespace and type information.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Elliotte Rusty Harold
 * @author Wesley Biggs
 * @author Victor Toni
 * @author Rolf Lear
 */
public class Attribute extends CloneBase implements Serializable, Cloneable {
  /**
   * JDOM 2.0.0 Serialization version. Attribute is simple
   */
  private static final long serialVersionUID = 200L;

  /**
   * The local name of the <code>Attribute</code>
   */
  protected String name;

  /**
   * The <code>{@link Namespace}</code> of the <code>Attribute</code>
   */
  protected Namespace namespace;

  /**
   * The value of the <code>Attribute</code>
   */
  protected String value;

  /**
   * The type of the <code>Attribute</code>
   */
  protected AttributeType type = AttributeType.UNDECLARED;

  /**
   * Specified attributes are part of the XML, unspecified attributes are 'defaulted' from a DTD.
   */
  protected boolean specified = true;

  /**
   * The parent to which this Attribute belongs. Change it with
   * {@link #setParent(Element)}
   */
  protected transient Element parent;

  /**
   * Default, no-args constructor for implementations to use if needed.
   */
  protected Attribute() {
  }

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name and value, and in the provided
   * <code>{@link Namespace}</code>.
   *
   * @param name      <code>String</code> name of <code>Attribute</code>.
   * @param value     <code>String</code> value for new attribute.
   * @param namespace <code>Namespace</code> namespace for new attribute.
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name or if is the new namespace is the default
   *                              namespace. Attributes cannot be in a default namespace.
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}).
   */
  public Attribute(final String name, final String value, final Namespace namespace) {
    this(name, value, AttributeType.UNDECLARED, namespace);
  }

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name, value, and type, and in the provided
   * <code>{@link Namespace}</code>.
   *
   * @param name      <code>String</code> name of <code>Attribute</code>.
   * @param value     <code>String</code> value for new attribute.
   * @param type      <code>AttributeType</code> for new attribute.
   * @param namespace <code>Namespace</code> namespace for new attribute.
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name or if is the new namespace is the default
   *                              namespace. Attributes cannot be in a default namespace.
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}) or
   *                              if the given attribute type is not one of the
   *                              supported types.
   */
  public Attribute(@NotNull String name, @NotNull String value, AttributeType type, Namespace namespace) {
    setName(name);
    setValue(value);
    setAttributeType(type);
    setNamespace(namespace);
  }

  @ApiStatus.Internal
  public Attribute(boolean ignored, @NotNull String name, @NotNull String value, @NotNull Namespace namespace) {
    this.name = name;
    this.value = value;
    this.namespace = namespace;
  }

  @ApiStatus.Internal
  protected Attribute(boolean ignored, @NotNull String name, @NotNull String value, @NotNull Namespace namespace, AttributeType type) {
    this(ignored, name, value, namespace);
    this.type = type;
  }

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name and value, and does not place
   * the attribute in a <code>{@link Namespace}</code>.
   * <p>
   * <b>Note</b>: This actually explicitly puts the
   * <code>Attribute</code> in the "empty" <code>Namespace</code>
   * (<code>{@link Namespace#NO_NAMESPACE}</code>).
   *
   * @param name  <code>String</code> name of <code>Attribute</code>.
   * @param value <code>String</code> value for new attribute.
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name.
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}).
   */
  public Attribute(@NotNull String name, @NotNull String value) {
    this(name, value, AttributeType.UNDECLARED, Namespace.NO_NAMESPACE);
  }

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name, value and type, and does not place
   * the attribute in a <code>{@link Namespace}</code>.
   * <p>
   * <b>Note</b>: This actually explicitly puts the
   * <code>Attribute</code> in the "empty" <code>Namespace</code>
   * (<code>{@link Namespace#NO_NAMESPACE}</code>).
   *
   * @param name  <code>String</code> name of <code>Attribute</code>.
   * @param value <code>String</code> value for new attribute.
   * @param type  <code>AttributeType</code> for new attribute.
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name.
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}) or
   *                              if the given attribute type is not one of the
   *                              supported types.
   */
  public Attribute(final String name, final String value, final AttributeType type) {
    this(name, value, type, Namespace.NO_NAMESPACE);
  }

  /**
   * This will return the parent of this <code>Attribute</code>.
   * If there is no parent, then this returns <code>null</code>.
   * Use return-type covariance to override Content's getParent() method
   * to return an Element, not just a Parent
   *
   * @return parent of this <code>Attribute</code>
   */
  public Element getParent() {
    return parent;
  }

  /**
   * Get this Attribute's Document.
   *
   * @return The document to which this Attribute is associated may be null.
   */
  public Document getDocument() {
    return parent == null ? null : parent.getDocument();
  }

  /**
   * This will retrieve the local name of the
   * <code>Attribute</code>. For any XML attribute
   * that appears as
   * <code>[namespacePrefix]:[attributeName]</code>,
   * the local name of the attribute would be
   * <code>[attributeName]</code>. When the attribute
   * has no namespace, the local name is simply the attribute
   * name.
   * <p>
   * To obtain the namespace prefix for this
   * attribute, the
   * <code>{@link #getNamespacePrefix()}</code>
   * method should be used.
   *
   * @return <code>String</code> - name of this attribute,
   * without any namespace prefix.
   */
  public String getName() {
    return name;
  }

  /**
   * This sets the local name of the <code>Attribute</code>.
   *
   * @param name the new local name to set
   * @return <code>Attribute</code> - the attribute modified.
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name.
   */
  public Attribute setName(final String name) {
    if (name == null) {
      throw new NullPointerException(
        "Can not set a null name for an Attribute.");
    }
    final String reason = Verifier.checkAttributeName(name);
    if (reason != null) {
      throw new IllegalNameException(name, "attribute", reason);
    }
    this.name = name;
    specified = true;
    return this;
  }

  /**
   * This will retrieve the qualified name of the <code>Attribute</code>.
   * For any XML attribute whose name is
   * <code>[namespacePrefix]:[elementName]</code>,
   * the qualified name of the attribute would be
   * everything (both namespace prefix and
   * element name). When the attribute has no
   * namespace, the qualified name is simply the attribute's
   * local name.
   * <p>
   * To obtain the local name of the attribute, the
   * <code>{@link #getName()}</code> method should be used.
   * <p>
   * To obtain the namespace prefix for this attribute,
   * the <code>{@link #getNamespacePrefix()}</code>
   * method should be used.
   *
   * @return <code>String</code> - full name for this element.
   */
  public String getQualifiedName() {
    // Note: Any changes here should be reflected in
    // XMLOutputter.printQualifiedName()
    final String prefix = namespace.getPrefix();

    // no prefix found
    if ("".equals(prefix)) {
      return getName();
    }
    return prefix +
           ':' +
           getName();
  }

  /**
   * This will retrieve the namespace prefix of the
   * <code>Attribute</code>. For any XML attribute
   * that appears as
   * <code>[namespacePrefix]:[attributeName]</code>,
   * the namespace prefix of the attribute would be
   * <code>[namespacePrefix]</code>. When the attribute
   * has no namespace, an empty <code>String</code> is returned.
   *
   * @return <code>String</code> - namespace prefix of this
   * attribute.
   */
  public String getNamespacePrefix() {
    return namespace.getPrefix();
  }

  /**
   * This returns the URI mapped to this <code>Attribute</code>'s
   * prefix. If no mapping is found, an empty <code>String</code> is
   * returned.
   *
   * @return <code>String</code> - namespace URI for this <code>Attribute</code>.
   */
  public String getNamespaceURI() {
    return namespace.getURI();
  }

  /**
   * This will return this <code>Attribute</code>'s
   * <code>{@link Namespace}</code>.
   *
   * @return <code>Namespace</code> - Namespace object for this <code>Attribute</code>
   */
  public Namespace getNamespace() {
    return namespace;
  }

  /**
   * This sets this <code>Attribute</code>'s <code>{@link Namespace}</code>.
   * If the provided namespace is null, the attribute will have no namespace.
   * The namespace must have a prefix.
   *
   * @param namespace the new namespace
   * @return <code>Element</code> - the element modified.
   * @throws IllegalNameException if the new namespace is the default namespace. Attributes cannot be in a default namespace.
   */
  public Attribute setNamespace(Namespace namespace) {
    if (namespace == null) {
      namespace = Namespace.NO_NAMESPACE;
    }

    // Verify the attribute isn't trying to be in a default namespace
    // Attributes can't be in a default namespace
    if (namespace != Namespace.NO_NAMESPACE &&
        "".equals(namespace.getPrefix())) {
      throw new IllegalNameException("", "attribute namespace",
                                     "An attribute namespace without a prefix can only be the " +
                                     "NO_NAMESPACE namespace");
    }
    this.namespace = namespace;
    specified = true;
    return this;
  }

  /**
   * This will return the actual textual value of this <code>Attribute</code>.  This will include all text within the quotation marks.
   *
   * @return <code>String</code> - value for this attribute.
   */
  public String getValue() {
    return value;
  }

  /**
   * This will set the value of the <code>Attribute</code>.
   *
   * @param value <code>String</code> value for the attribute.
   * @return <code>Attribute</code> - this Attribute modified.
   * @throws IllegalDataException if the given attribute value is illegal character data
   * (as determined by {@link Verifier#checkCharacterData}).
   */
  public Attribute setValue(@NotNull String value) {
    String reason = Verifier.checkCharacterData(value);
    if (reason != null) {
      throw new IllegalDataException(value, "attribute", reason);
    }

    this.value = value;
    specified = true;
    return this;
  }

  /**
   * This will return the declared type of this <code>Attribute</code>.
   *
   * @return <code>AttributeType</code> - type for this attribute.
   */
  public AttributeType getAttributeType() {
    return type;
  }

  /**
   * This will set the type of the <code>Attribute</code>.
   *
   * @param type <code>int</code> type for the attribute.
   * @return <code>Attribute</code> - this Attribute modified.
   * @throws IllegalDataException if the given attribute type is not one of the supported types.
   */
  public Attribute setAttributeType(final AttributeType type) {
    this.type = type == null ? AttributeType.UNDECLARED : type;
    specified = true;
    return this;
  }

  /**
   * Get the 'specified' flag. True values indicate this attribute was part of an XML document; false indicates it was defaulted from a DTD.
   *
   * @return the specified flag.
   * @since JDOM2
   */
  public boolean isSpecified() {
    return specified;
  }

  /**
   * Change the specified flag to the given value.
   *
   * @param specified The value to set the specified flag to.
   * @since JDOM2
   */
  public void setSpecified(boolean specified) {
    this.specified = specified;
  }

  /**
   * This returns a <code>String</code> representation of the
   * <code>Attribute</code>, suitable for debugging.
   *
   * @return <code>String</code> - information about the
   * <code>Attribute</code>
   */
  @Override
  public String toString() {
    return "[Attribute: " +
           getQualifiedName() +
           "=\"" +
           value +
           "\"" +
           "]";
  }

  @Override
  public Attribute clone() {
    final Attribute clone = (Attribute)super.clone();
    clone.parent = null;
    return clone;
  }

  /**
   * Detach this Attribute from its parent.
   *
   * @return this Attribute (detached).
   */
  public Attribute detach() {
    if (parent != null) {
      parent.removeAttribute(this);
    }
    return this;
  }

  /**
   * Set this Attribute's parent. This is not public!
   *
   * @param parent The parent to set
   * @return this Attribute (state may be indeterminate depending on whether this has been included in the Element's list yet).
   */
  protected Attribute setParent(Element parent) {
    this.parent = parent;
    return this;
  }


  /////////////////////////////////////////////////////////////////
  // Convenience Methods below here
  /////////////////////////////////////////////////////////////////

  /**
   * This gets the value of the attribute, in
   * <code>int</code> form, and if no conversion
   * can occur, throws a
   * <code>{@link DataConversionException}</code>
   *
   * @return <code>int</code> value of attribute.
   * @throws DataConversionException when conversion fails.
   */
  public int getIntValue() throws DataConversionException {
    try {
      return Integer.parseInt(value.trim());
    }
    catch (final NumberFormatException e) {
      throw new DataConversionException(name, "int");
    }
  }

  /**
   * This gets the value of the attribute, in
   * <code>long</code> form, and if no conversion
   * can occur, throws a
   * <code>{@link DataConversionException}</code>
   *
   * @return <code>long</code> value of attribute.
   * @throws DataConversionException when conversion fails.
   */
  public long getLongValue() throws DataConversionException {
    try {
      return Long.parseLong(value.trim());
    }
    catch (final NumberFormatException e) {
      throw new DataConversionException(name, "long");
    }
  }

  /**
   * This gets the value of the attribute, in
   * <code>float</code> form, and if no conversion
   * can occur, throws a
   * <code>{@link DataConversionException}</code>
   *
   * @return <code>float</code> value of attribute.
   * @throws DataConversionException when conversion fails.
   */
  public float getFloatValue() throws DataConversionException {
    try {
      // Avoid Float.parseFloat() to support JDK 1.1
      return Float.valueOf(value.trim()).floatValue();
    }
    catch (final NumberFormatException e) {
      throw new DataConversionException(name, "float");
    }
  }

  /**
   * This gets the value of the attribute, in
   * <code>double</code> form, and if no conversion
   * can occur, throws a
   * <code>{@link DataConversionException}</code>
   *
   * @return <code>double</code> value of attribute.
   * @throws DataConversionException when conversion fails.
   */
  public double getDoubleValue() throws DataConversionException {
    try {
      // Avoid Double.parseDouble() to support JDK 1.1
      return Double.valueOf(value.trim()).doubleValue();
    }
    catch (final NumberFormatException e) {
      // Specially handle INF and -INF that Double.valueOf doesn't do
      String v = value.trim();
      if ("INF".equals(v)) {
        return Double.POSITIVE_INFINITY;
      }
      if ("-INF".equals(v)) {
        return Double.NEGATIVE_INFINITY;
      }
      throw new DataConversionException(name, "double");
    }
  }

  /**
   * This gets the effective boolean value of the attribute, or throws a
   * <code>{@link DataConversionException}</code> if a conversion can't be
   * performed.  True values are: "true", "on", "1", and "yes".  False
   * values are: "false", "off", "0", and "no".  Values are trimmed before
   * comparison.  Values other than those listed here throw the exception.
   *
   * @return <code>boolean</code> value of attribute.
   * @throws DataConversionException when conversion fails.
   */
  public boolean getBooleanValue() throws DataConversionException {
    final String valueTrim = value.trim();
    if (
      (valueTrim.equalsIgnoreCase("true")) ||
      (valueTrim.equalsIgnoreCase("on")) ||
      (valueTrim.equalsIgnoreCase("1")) ||
      (valueTrim.equalsIgnoreCase("yes"))) {
      return true;
    }
    else if (
      (valueTrim.equalsIgnoreCase("false")) ||
      (valueTrim.equalsIgnoreCase("off")) ||
      (valueTrim.equalsIgnoreCase("0")) ||
      (valueTrim.equalsIgnoreCase("no"))
    ) {
      return false;
    }
    else {
      throw new DataConversionException(name, "boolean");
    }
  }
}
