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
 * Creates the standard top-level JDOM classes (Element, Document, Comment,
 * etc). A subclass of this factory might construct custom classes.
 *
 * @author Ken Rune Holland
 * @author Phil Nelson
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
public class DefaultJDOMFactory implements JDOMFactory {
  /**
   * Creates a new DefaultJDOMFactory instance.
   */
  public DefaultJDOMFactory() {
    // do nothing special
  }

  // Allow Javadocs to inherit from JDOMFactory

  @Override
  public Attribute attribute(String name, String value, Namespace namespace) {
    return new Attribute(name, value, namespace);
  }

  @Override
  public Attribute attribute(String name, String value, AttributeType type,
                             Namespace namespace) {
    return new Attribute(name, value, type, namespace);
  }

  @Override
  public Attribute attribute(String name, String value) {
    return new Attribute(name, value);
  }

  @Override
  public Attribute attribute(String name, String value, AttributeType type) {
    return new Attribute(name, value, type);
  }

  @Override
  public final CDATA cdata(String str) {
    return cdata(-1, -1, str);
  }

  @Override
  public CDATA cdata(final int line, final int col, final String text) {
    return new CDATA(text);
  }

  @Override
  public final Text text(String str) {
    return text(-1, -1, str);
  }

  @Override
  public Text text(final int line, final int col, final String text) {
    return new Text(text);
  }

  @Override
  public final Comment comment(String text) {
    return comment(-1, -1, text);
  }

  @Override
  public Comment comment(final int line, final int col, final String text) {
    return new Comment(text);
  }

  @Override
  public final DocType docType(String elementName, String publicID, String systemID) {
    return docType(-1, -1, elementName, publicID, systemID);
  }

  @Override
  public DocType docType(final int line, final int col,
                         final String elementName, String publicID, String systemID) {
    return new DocType(elementName, publicID, systemID);
  }

  @Override
  public final DocType docType(String elementName, String systemID) {
    return docType(-1, -1, elementName, systemID);
  }

  @Override
  public DocType docType(final int line, final int col,
                         final String elementName, String systemID) {
    return new DocType(elementName, systemID);
  }

  @Override
  public final DocType docType(String elementName) {
    return docType(-1, -1, elementName);
  }

  @Override
  public DocType docType(final int line, final int col,
                         final String elementName) {
    return new DocType(elementName);
  }

  @Override
  public Document document(Element rootElement, DocType docType) {
    return new Document(rootElement, docType);
  }

  @Override
  public Document document(Element rootElement, DocType docType,
                           String baseURI) {
    return new Document(rootElement, docType, baseURI);
  }

  @Override
  public Document document(Element rootElement) {
    return new Document(rootElement);
  }

  @Override
  public Element element(String name, Namespace namespace) {
    return element(-1, -1, name, namespace);
  }

  @Override
  public Element element(final int line, final int col, final String name,
                         Namespace namespace) {
    return new Element(name, namespace);
  }

  @Override
  public Element element(String name) {
    return element(-1, -1, name);
  }

  @Override
  public Element element(final int line, final int col, final String name) {
    return new Element(name);
  }

  @Override
  public Element element(String name, String uri) {
    return element(-1, -1, name, uri);
  }

  @Override
  public Element element(final int line, final int col, final String name,
                         String uri) {
    return new Element(name, uri);
  }

  @Override
  public Element element(String name, String prefix, String uri) {
    return element(-1, -1, name, prefix, uri);
  }

  @Override
  public Element element(final int line, final int col, final String name,
                         String prefix, String uri) {
    return new Element(name, prefix, uri);
  }

  @Override
  public final ProcessingInstruction processingInstruction(String target,
                                                           String data) {
    return processingInstruction(-1, -1, target, data);
  }

  @Override
  public ProcessingInstruction processingInstruction(final int line,
                                                     final int col, final String target, String data) {
    return new ProcessingInstruction(target, data);
  }

  @Override
  public final EntityRef entityRef(String name) {
    return entityRef(-1, -1, name);
  }

  @Override
  public EntityRef entityRef(final int line, final int col, final String name) {
    return new EntityRef(name);
  }

  @Override
  public final EntityRef entityRef(String name, String publicID, String systemID) {
    return entityRef(-1, -1, name, publicID, systemID);
  }

  @Override
  public EntityRef entityRef(final int line, final int col,
                             final String name, String publicID, String systemID) {
    return new EntityRef(name, publicID, systemID);
  }

  @Override
  public void addContent(Parent parent, Content child) {
    if (parent instanceof Document) {
      ((Document)parent).addContent(child);
    }
    else {
      ((Element)parent).addContent(child);
    }
  }

  @Override
  public void setAttribute(Element parent, Attribute a) {
    parent.setAttribute(a);
  }

  @Override
  public void addNamespaceDeclaration(Element parent, Namespace additional) {
    parent.addNamespaceDeclaration(additional);
  }

  @Override
  public void setRoot(Document doc, Element root) {
    doc.setRootElement(root);
  }
}
