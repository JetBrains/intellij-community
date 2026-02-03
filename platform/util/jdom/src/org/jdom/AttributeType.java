/*--

 Copyright (C) 2011-2012 Jason Hunter & Brett McLaughlin.
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

import org.xml.sax.Attributes;

/**
 * Use a simple enumeration for the Attribute Types
 *
 * @author Rolf Lear
 */
public enum AttributeType {
  /**
   * Attribute type: the attribute has not been declared or type
   * is unknown.
   *
   * @see #getAttributeType
   */
  UNDECLARED,

  /**
   * Attribute type: the attribute value is a string.
   *
   * @see Attribute#getAttributeType
   */
  CDATA,

  /**
   * Attribute type: the attribute value is a unique identifier.
   *
   * @see #getAttributeType
   */
  ID,

  /**
   * Attribute type: the attribute value is a reference to a
   * unique identifier.
   *
   * @see #getAttributeType
   */
  IDREF,

  /**
   * Attribute type: the attribute value is a list of references to
   * unique identifiers.
   *
   * @see #getAttributeType
   */
  IDREFS,
  /**
   * Attribute type: the attribute value is the name of an entity.
   *
   * @see #getAttributeType
   */
  ENTITY,
  /**
   * <p>
   * Attribute type: the attribute value is a list of entity names.
   * </p>
   *
   * @see #getAttributeType
   */
  ENTITIES,

  /**
   * Attribute type: the attribute value is a name token.
   * <p>
   * According to SAX 2.0 specification, attributes of enumerated
   * types should be reported as "NMTOKEN" by SAX parsers.  But the
   * major parsers (Xerces and Crimson) provide specific values
   * that permit to recognize them as {@link #ENUMERATION}.
   *
   * @see Attribute#getAttributeType
   */
  NMTOKEN,
  /**
   * Attribute type: the attribute value is a list of name tokens.
   *
   * @see #getAttributeType
   */
  NMTOKENS,
  /**
   * Attribute type: the attribute value is the name of a notation.
   *
   * @see #getAttributeType
   */
  NOTATION,
  /**
   * Attribute type: the attribute value is a name token from an
   * enumeration.
   *
   * @see #getAttributeType
   */
  ENUMERATION;

  /**
   * Returns the JDOM AttributeType value from the SAX 2.0
   * attribute type string provided by the parser.
   *
   * @param typeName <code>String</code> the SAX 2.0 attribute
   *                 type string.
   * @return <code>int</code> the JDOM attribute type.
   * @see Attribute#setAttributeType
   * @see Attributes#getType
   */
  public static AttributeType getAttributeType(String typeName) {
    if (typeName == null) {
      return UNDECLARED;
    }

    try {
      return valueOf(typeName);
    }
    catch (IllegalArgumentException iae) {
      if (!typeName.isEmpty() && typeName.trim().charAt(0) == '(') {
        // Xerces 1.4.X reports attributes of enumerated type with
        // a type string equals to the enumeration definition, i.e.
        // starting with a parenthesis.
        return ENUMERATION;
      }
      return UNDECLARED;
    }
  }
}
