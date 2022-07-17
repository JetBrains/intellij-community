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

/**
 * An interface to be used by builders when constructing JDOM objects. The
 * <code>DefaultJDOMFactory</code> creates the standard top-level JDOM classes
 * (Element, Document, Comment, etc). Another implementation of this factory
 * could be used to create custom classes.
 *
 * @author Ken Rune Holland
 * @author Phil Nelson
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
public interface JDOMFactory {
  // **** constructing Attributes ****

  /**
   * <p>
   * This will create a new <code>Attribute</code> with the
   * specified (local) name and value, and in the provided
   * <code>{@link Namespace}</code>.
   * </p>
   *
   * @param name      <code>String</code> name of <code>Attribute</code>.
   * @param value     <code>String</code> value for new attribute.
   * @param namespace <code>Namespace</code> of the new Attribute
   * @return the created Attribute instance
   */
  Attribute attribute(String name, String value, Namespace namespace);

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name, value, and type, and in the provided
   * <code>{@link Namespace}</code>.
   *
   * @param name      <code>String</code> name of <code>Attribute</code>.
   * @param value     <code>String</code> value for new attribute.
   * @param type      <code>AttributeType</code> type for new attribute.
   * @param namespace <code>Namespace</code> namespace for new attribute.
   * @return the created Attribute instance
   */
  Attribute attribute(String name, String value,
                      AttributeType type, Namespace namespace);

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name and value, and does not place
   * the attribute in a <code>{@link Namespace}</code>.
   * <p>
   * <b>Note</b>: This actually explicitly puts the
   * <code>Attribute</code> in the "empty" <code>Namespace</code>
   * (<code>{@link Namespace#NO_NAMESPACE}</code>).
   * </p>
   *
   * @param name  <code>String</code> name of <code>Attribute</code>.
   * @param value <code>String</code> value for new attribute.
   * @return the created Attribute instance
   */
  Attribute attribute(String name, String value);

  /**
   * This will create a new <code>Attribute</code> with the
   * specified (local) name, value and type, and does not place
   * the attribute in a <code>{@link Namespace}</code>.
   * <p>
   * <b>Note</b>: This actually explicitly puts the
   * <code>Attribute</code> in the "empty" <code>Namespace</code>
   * (<code>{@link Namespace#NO_NAMESPACE}</code>).
   * </p>
   *
   * @param name  <code>String</code> name of <code>Attribute</code>.
   * @param value <code>String</code> value for new attribute.
   * @param type  <code>AttributeType</code> type for new attribute.
   * @return the created Attribute instance
   */
  Attribute attribute(String name, String value, AttributeType type);

  // **** constructing CDATA ****

  /**
   * This creates the CDATA with the supplied text.
   *
   * @param str <code>String</code> content of CDATA.
   * @return the created CDATA instance
   */
  CDATA cdata(String str);

  /**
   * This creates the CDATA with the supplied text.
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param str  <code>String</code> content of CDATA.
   * @return the created CDATA instance
   * @since JDOM2
   */
  CDATA cdata(int line, int col, String str);

  // **** constructing Text ****

  /**
   * This creates the Text with the supplied text.
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param str  <code>String</code> content of Text.
   * @return the created Text instance
   * @since JDOM2
   */
  Text text(int line, int col, String str);

  // **** constructing Text ****

  /**
   * This creates the Text with the supplied text.
   *
   * @param str <code>String</code> content of Text.
   * @return the created Text instance
   */
  Text text(String str);

  // **** constructing Comment ****

  /**
   * This creates the comment with the supplied text.
   *
   * @param text <code>String</code> content of comment.
   * @return the created Comment instance
   */
  Comment comment(String text);

  /**
   * This creates the comment with the supplied text.
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param text <code>String</code> content of comment.
   * @return the created Comment instance
   * @since JDOM2
   */
  Comment comment(int line, int col, String text);

  // **** constructing DocType

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
   * @return the created DocType instance
   */
  DocType docType(String elementName,
                  String publicID, String systemID);

  /**
   * This will create the <code>DocType</code> with
   * the specified element name and reference to an
   * external DTD.
   *
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @param systemID    <code>String</code> system ID of
   *                    referenced DTD
   * @return the created DocType instance
   */
  DocType docType(String elementName, String systemID);

  /**
   * This will create the <code>DocType</code> with
   * the specified element name
   *
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @return the created DocType instance
   */
  DocType docType(String elementName);

  /**
   * This will create the <code>DocType</code> with
   * the specified element name and a reference to an
   * external DTD.
   *
   * @param line        The line on which this content begins.
   * @param col         The column on the line at which this content begins.
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @param publicID    <code>String</code> public ID of
   *                    referenced DTD
   * @param systemID    <code>String</code> system ID of
   *                    referenced DTD
   * @return the created DocType instance
   * @since JDOM2
   */
  DocType docType(int line, int col, String elementName,
                  String publicID, String systemID);

  /**
   * This will create the <code>DocType</code> with
   * the specified element name and reference to an
   * external DTD.
   *
   * @param line        The line on which this content begins.
   * @param col         The column on the line at which this content begins.
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @param systemID    <code>String</code> system ID of
   *                    referenced DTD
   * @return the created DocType instance
   * @since JDOM2
   */
  DocType docType(int line, int col, String elementName, String systemID);

  /**
   * This will create the <code>DocType</code> with
   * the specified element name
   *
   * @param line        The line on which this content begins.
   * @param col         The column on the line at which this content begins.
   * @param elementName <code>String</code> name of
   *                    element being constrained.
   * @return the created DocType instance
   * @since JDOM2
   */
  DocType docType(int line, int col, String elementName);

  // **** constructing Document

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element and the supplied
   * <code>{@link DocType}</code> declaration.
   *
   * @param rootElement <code>Element</code> for document root.
   * @param docType     <code>DocType</code> declaration.
   * @return the created Document instance
   */
  Document document(Element rootElement, DocType docType);

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element and the supplied
   * <code>{@link DocType}</code> declaration.
   *
   * @param rootElement <code>Element</code> for document root.
   * @param docType     <code>DocType</code> declaration.
   * @param baseURI     the URI from which this document was loaded.
   * @return the created Document instance
   */
  Document document(Element rootElement, DocType docType, String baseURI);

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element, and no <code>{@link DocType}</code>
   * declaration.
   *
   * @param rootElement <code>Element</code> for document root
   * @return the created Document instance
   */
  Document document(Element rootElement);

  // **** constructing Elements ****

  /**
   * This will create a new <code>Element</code>
   * with the supplied (local) name, and define
   * the <code>{@link Namespace}</code> to be used.
   *
   * @param name      <code>String</code> name of element.
   * @param namespace <code>Namespace</code> to put element in.
   * @return the created Element instance
   */
  Element element(String name, Namespace namespace);

  /**
   * This will create an <code>Element</code> in no
   * <code>{@link Namespace}</code>.
   *
   * @param name <code>String</code> name of element.
   * @return the created Element instance
   */
  Element element(String name);

  /**
   * This will create a new <code>Element</code> with
   * the supplied (local) name, and specifies the URI
   * of the <code>{@link Namespace}</code> the <code>Element</code>
   * should be in, resulting it being unprefixed (in the default
   * namespace).
   *
   * @param name <code>String</code> name of element.
   * @param uri  <code>String</code> URI for <code>Namespace</code> element
   *             should be in.
   * @return the created Element instance
   */
  Element element(String name, String uri);

  /**
   * This will create a new <code>Element</code> with
   * the supplied (local) name, and specifies the prefix and URI
   * of the <code>{@link Namespace}</code> the <code>Element</code>
   * should be in.
   *
   * @param name   <code>String</code> name of element.
   * @param prefix the NamespacePrefix to use for this Element
   * @param uri    <code>String</code> URI for <code>Namespace</code> element
   *               should be in.
   * @return the created Element instance
   */
  Element element(String name, String prefix, String uri);

  /**
   * This will create a new <code>Element</code>
   * with the supplied (local) name, and define
   * the <code>{@link Namespace}</code> to be used.
   *
   * @param line      The line on which this content begins.
   * @param col       The column on the line at which this content begins.
   * @param name      <code>String</code> name of element.
   * @param namespace <code>Namespace</code> to put element in.
   * @return the created Element instance
   * @since JDOM2
   */
  Element element(int line, int col, String name, Namespace namespace);

  /**
   * This will create an <code>Element</code> in no
   * <code>{@link Namespace}</code>.
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param name <code>String</code> name of element.
   * @return the created Element instance
   * @since JDOM2
   */
  Element element(int line, int col, String name);

  /**
   * This will create a new <code>Element</code> with
   * the supplied (local) name, and specifies the URI
   * of the <code>{@link Namespace}</code> the <code>Element</code>
   * should be in, resulting it being unprefixed (in the default
   * namespace).
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param name <code>String</code> name of element.
   * @param uri  <code>String</code> URI for <code>Namespace</code> element
   *             should be in.
   * @return the created Element instance
   * @since JDOM2
   */
  Element element(int line, int col, String name, String uri);

  /**
   * This will create a new <code>Element</code> with
   * the supplied (local) name, and specifies the prefix and URI
   * of the <code>{@link Namespace}</code> the <code>Element</code>
   * should be in.
   *
   * @param line   The line on which this content begins.
   * @param col    The column on the line at which this content begins.
   * @param name   <code>String</code> name of element.
   * @param prefix the NamespacePrefix to use for this Element
   * @param uri    <code>String</code> URI for <code>Namespace</code> element
   *               should be in.
   * @return the created Element instance
   * @since JDOM2
   */
  Element element(int line, int col, String name, String prefix, String uri);

  // **** constructing ProcessingInstruction ****

  /**
   * This will create a new <code>ProcessingInstruction</code>
   * with the specified target and data.
   *
   * @param target <code>String</code> target of PI.
   * @param data   <code>String</code> data for PI.
   * @return the created ProcessingInstruction instance
   */
  ProcessingInstruction processingInstruction(String target,
                                              String data);

  /**
   * This will create a new <code>ProcessingInstruction</code>
   * with the specified target and data.
   *
   * @param line   The line on which this content begins.
   * @param col    The column on the line at which this content begins.
   * @param target <code>String</code> target of PI.
   * @param data   <code>String</code> data for PI.
   * @return the created ProcessingInstruction instance
   * @since JDOM2
   */
  ProcessingInstruction processingInstruction(int line, int col, String target,
                                              String data);

  /**
   * This will create a new <code>EntityRef</code>
   * with the supplied name.
   *
   * @param name <code>String</code> name of element.
   * @return the created EntityRef instance
   */
  EntityRef entityRef(String name);

  /**
   * This will create a new <code>EntityRef</code>
   * with the supplied name, public ID, and system ID.
   *
   * @param name     <code>String</code> name of element.
   * @param publicID <code>String</code> public ID of element.
   * @param systemID <code>String</code> system ID of element.
   * @return the created EntityRef instance
   */
  EntityRef entityRef(String name, String publicID, String systemID);

  /**
   * This will create a new <code>EntityRef</code>
   * with the supplied name.
   *
   * @param line The line on which this content begins.
   * @param col  The column on the line at which this content begins.
   * @param name <code>String</code> name of element.
   * @return the created EntityRef instance
   * @since JDOM2
   */
  EntityRef entityRef(int line, int col, String name);

  /**
   * This will create a new <code>EntityRef</code>
   * with the supplied name, public ID, and system ID.
   *
   * @param line     The line on which this content begins.
   * @param col      The column on the line at which this content begins.
   * @param name     <code>String</code> name of element.
   * @param publicID <code>String</code> public ID of element.
   * @param systemID <code>String</code> system ID of element.
   * @return the created EntityRef instance
   * @since JDOM2
   */
  EntityRef entityRef(int line, int col, String name, String publicID, String systemID);

  // =====================================================================
  // List manipulation
  // =====================================================================

  /**
   * This will add the specified content to the specified parent instance
   *
   * @param parent  The {@link Parent} to add the content to.
   * @param content The {@link Content} to add
   */
  void addContent(Parent parent, Content content);

  /**
   * Sets a specific Attribute on an Element
   *
   * @param element The {@link Element} to set the Attribute on
   * @param a       The {@link Attribute} to set
   */
  void setAttribute(Element element, Attribute a);

  /**
   * Adds a namespace declaration to an Element
   *
   * @param element    The {@link Element} to add the Namespace to
   * @param additional The {@link Namespace} to add.
   */
  void addNamespaceDeclaration(Element element, Namespace additional);

  /**
   * Sets the 'root' Element for a Document.
   *
   * @param doc  The {@link Document} to set the Root Element of.
   * @param root The {@link Element} to set as the root.
   */
  void setRoot(Document doc, Element root);
}
