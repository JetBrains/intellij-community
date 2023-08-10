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

import org.jdom.output.XMLOutputter;

/**
 * An XML DOCTYPE declaration.  Method allows the user to get and set the
 * root element name, public id, system id and internal subset.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 */
public final class DocType extends Content {
  /**
   * JDOM2 Serialization. In this case, DocType is simple.
   */
  private static final long serialVersionUID = 200L;

  /**
   * The element being constrained
   *
   * @serialField The root element name
   */
  private String elementName;

  /**
   * The public ID of the DOCTYPE
   *
   * @serialField The PublicID, may be null
   */
  private String publicID;

  /**
   * The system ID of the DOCTYPE
   *
   * @serialField The SystemID, may be null
   */
  private String systemID;

  /**
   * The internal subset of the DOCTYPE
   *
   * @serialField The InternalSubset, may be null
   */
  private String internalSubset;

  /*
   * XXX:
   *   We need to take care of entities and notations here.
   */

  /**
   * This will create the <code>DocType</code> with
   * the specified element name and a reference to an
   * external DTD.
   *
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @param publicID    <code>String</code> public ID of
   *                    referenced DTD
   * @param systemID    <code>String</code> system ID of
   *                    referenced DTD
   * @throws IllegalDataException if the given system ID is not a legal
   *                              system literal or the public ID is not a legal public ID.
   * @throws IllegalNameException if the given root element name is not a
   *                              legal XML element name.
   */
  public DocType(String elementName, String publicID, String systemID) {
    super(CType.DocType);
    setElementName(elementName);
    setPublicID(publicID);
    setSystemID(systemID);
  }

  /**
   * This will create the <code>DocType</code> with
   * the specified element name and reference to an
   * external DTD.
   *
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @param systemID    <code>String</code> system ID of
   *                    referenced DTD
   * @throws IllegalDataException if the given system ID is not a legal
   *                              system literal.
   * @throws IllegalNameException if the given root element name is not a
   *                              legal XML element name.
   */
  public DocType(String elementName, String systemID) {
    this(elementName, null, systemID);
  }

  /**
   * This will create the <code>DocType</code> with
   * the specified element name
   *
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @throws IllegalNameException if the given root element name is not a
   *                              legal XML element name.
   */
  public DocType(String elementName) {
    this(elementName, null, null);
  }

  /**
   * This will retrieve the element name being constrained.
   *
   * @return <code>String</code> - element name for DOCTYPE
   */
  public String getElementName() {
    return elementName;
  }

  /**
   * This will set the root element name declared by this
   * DOCTYPE declaration.
   *
   * @param elementName <code>String</code> name of
   *                    root element being constrained.
   * @return this <code>DocType</code> instance
   * @throws IllegalNameException if the given root element name is not a
   *                              legal XML element name.
   */
  public DocType setElementName(String elementName) {
    // This can contain a colon, so we use checkXMLName()
    // instead of checkElementName()
    String reason = Verifier.checkXMLName(elementName);
    if (reason != null) {
      throw new IllegalNameException(elementName, "DocType", reason);
    }
    this.elementName = elementName;
    return this;
  }

  /**
   * This will retrieve the public ID of an externally
   * referenced DTD, or an empty <code>String</code> if
   * none is referenced.
   *
   * @return <code>String</code> - public ID of referenced DTD.
   */
  public String getPublicID() {
    return publicID;
  }

  /**
   * This will set the public ID of an externally
   * referenced DTD.
   *
   * @param publicID id to set
   * @return DocType <code>DocType</code> this DocType object
   * @throws IllegalDataException if the given public ID is not a legal
   *                              public ID.
   */
  public DocType setPublicID(String publicID) {
    String reason = Verifier.checkPublicID(publicID);
    if (reason != null) {
      throw new IllegalDataException(publicID, "DocType", reason);
    }
    this.publicID = publicID;

    return this;
  }

  /**
   * This will retrieve the system ID of an externally
   * referenced DTD, or an empty <code>String</code> if
   * none is referenced.
   *
   * @return <code>String</code> - system ID of referenced DTD.
   */
  public String getSystemID() {
    return systemID;
  }

  /**
   * This will set the system ID of an externally
   * referenced DTD.
   *
   * @param systemID id to set
   * @return systemID <code>String</code> system ID of
   * referenced DTD.
   * @throws IllegalDataException if the given system ID is not a legal
   *                              system literal.
   */
  public DocType setSystemID(String systemID) {
    String reason = Verifier.checkSystemLiteral(systemID);
    if (reason != null) {
      throw new IllegalDataException(systemID, "DocType", reason);
    }
    this.systemID = systemID;

    return this;
  }

  /**
   * Returns the empty string since types don't have an XPath
   * 1.0 string value.
   *
   * @return the empty string
   */
  @Override
  public String getValue() {
    return "";  // doctypes don't have an XPath string value
  }

  /**
   * This sets the data for the internal subset.
   *
   * @param newData data for the internal subset, as a
   *                <code>String</code>.
   */
  public void setInternalSubset(String newData) {
    internalSubset = newData;
  }

  /**
   * This returns the data for the internal subset.
   *
   * @return <code>String</code> - the internal subset
   */
  public String getInternalSubset() {
    return internalSubset;
  }

  /**
   * This returns a <code>String</code> representation of the
   * <code>DocType</code>, suitable for debugging.
   *
   * @return <code>String</code> - information about the
   * <code>DocType</code>
   */
  @Override
  public String toString() {
    return "[DocType: " + new XMLOutputter().outputString(this) + "]";
  }

  @Override
  public DocType clone() {
    return (DocType)super.clone();
  }

  @Override
  public DocType detach() {
    return (DocType)super.detach();
  }

  @Override
  protected DocType setParent(Parent parent) {
    return (DocType)super.setParent(parent);
  }

  @Override
  public Document getParent() {
    // because DocType can only be attached to a Document.
    return (Document)super.getParent();
  }
}
