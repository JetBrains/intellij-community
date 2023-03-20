/*-- 

 Copyright (C) 2000-2007 Jason Hunter & Brett McLaughlin.
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

package org.jdom.output.support;

import org.jdom.*;
import org.jdom.Content.CType;
import org.jdom.output.DOMOutputter;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.util.NamespaceStack;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class provides a concrete implementation of {@link DOMOutputProcessor}
 * for supporting the {@link DOMOutputter}.
 * <p>
 * <h2>Overview</h2>
 * <p>
 * This class is marked abstract even though all methods are fully implemented.
 * The <code>process*(...)</code> methods are public because they match the
 * DOMOutputProcessor interface but the remaining methods are all protected.
 * <p>
 * People who want to create a custom DOMOutputProcessor for DOMOutputter
 * are able to extend this class and modify any functionality they want. Before
 * sub-classing this you should first check to see if the {@link Format} class
 * can get you the results you want.
 * <p>
 * <b><i>Subclasses of this should have reentrant methods.</i></b> This is
 * easiest to accomplish simply by not allowing any instance fields. If your
 * subclass has an instance field/variable, then it's probably broken.
 * <p>
 * <h2>The Stacks</h2>
 * <p>
 * One significant feature of this implementation is that it creates and
 * maintains both a {@link NamespaceStack} and {@link FormatStack} that are
 * managed in the
 * {@link #printElement(FormatStack, NamespaceStack, org.w3c.dom.Document, Element)}
 * method. The stacks are pushed and popped in that method only. They
 * significantly improve the performance and readability of the code.
 * <p>
 * The NamespaceStack is only sent through to the
 * {@link #printElement(FormatStack, NamespaceStack, org.w3c.dom.Document, Element)}
 * and
 * {@link #printContent(FormatStack, NamespaceStack, org.w3c.dom.Document, Node, Walker)}
 * methods, but the FormatStack is pushed through to all print* Methods.
 * <p>
 * <h2>Content Processing</h2>
 * <p>
 * This class delegates the formatting of the content to the Walker classes,
 * and you can create your own custom walker by overriding the
 * {@link #buildWalker(FormatStack, List, boolean)} method.
 *
 * @author Rolf Lear
 * @see DOMOutputter
 * @see DOMOutputProcessor
 * @since JDOM2
 */
public abstract class AbstractDOMOutputProcessor extends
                                                 AbstractOutputProcessor implements DOMOutputProcessor {

  /**
   * This will handle adding any <code>{@link Namespace}</code> attributes to
   * the DOM tree.
   *
   * @param ns <code>Namespace</code> to add definition of
   */
  private static String getXmlnsTagFor(Namespace ns) {
    String attrName = "xmlns";
    if (!ns.getPrefix().isEmpty()) {
      attrName += ":";
      attrName += ns.getPrefix();
    }
    return attrName;
  }

  /* *******************************************
   * DOMOutputProcessor implementation.
   * *******************************************
   */

  @Override
  public org.w3c.dom.Document process(org.w3c.dom.Document basedoc, Format format, Document doc) {
    return printDocument(new FormatStack(format), new NamespaceStack(),
                         basedoc, doc);
  }

  @Override
  public org.w3c.dom.Element process(org.w3c.dom.Document basedoc, Format format, Element element) {
    return printElement(new FormatStack(format), new NamespaceStack(), basedoc, element);
  }

  @Override
  public List<Node> process(org.w3c.dom.Document basedoc, Format format, List<? extends Content> list) {
    List<Node> ret = new ArrayList<>(list.size());
    FormatStack formatStack = new FormatStack(format);
    NamespaceStack namespaceStack = new NamespaceStack();
    for (Content c : list) {
      formatStack.push();
      try {
        Node node = helperContentDispatcher(formatStack, namespaceStack,
                                            basedoc, c);
        if (node != null) {
          ret.add(node);
        }
      }
      finally {
        formatStack.pop();
      }
    }
    return ret;
  }

  @Override
  public CDATASection process(org.w3c.dom.Document document, Format format, CDATA cdata) {
    final List<CDATA> list = Collections.singletonList(cdata);
    final FormatStack formatStack = new FormatStack(format);
    final Walker walker = buildWalker(formatStack, list, false);
    if (walker.hasNext()) {
      final Content c = walker.next();
      if (c == null) {
        return printCDATA(document, new CDATA(walker.text()));
      }
      if (c.getCType() == CType.CDATA) {
        return printCDATA(document, (CDATA)c);
      }
    }
    // return an empty string if nothing happened.
    return null;
  }

  @Override
  public org.w3c.dom.Text process(org.w3c.dom.Document document, Format format, Text text) {
    final List<Text> list = Collections.singletonList(text);
    final FormatStack formatStack = new FormatStack(format);
    final Walker walker = buildWalker(formatStack, list, false);
    if (walker.hasNext()) {
      final Content c = walker.next();
      if (c == null) {
        return printText(document, new Text(walker.text()));
      }
      if (c.getCType() == CType.Text) {
        return printText(document, (Text)c);
      }
    }
    // return an empty string if nothing happened.
    return null;
  }

  @Override
  public org.w3c.dom.Comment process(org.w3c.dom.Document document, Format format, Comment comment) {
    return printComment(document, comment);
  }

  @Override
  public org.w3c.dom.ProcessingInstruction process(org.w3c.dom.Document document, Format format,
    ProcessingInstruction pi) {
    return printProcessingInstruction(document, pi);
  }

  @Override
  public EntityReference process(org.w3c.dom.Document basedoc, Format format, EntityRef entity) {
    return printEntityRef(basedoc, entity);
  }

  @Override
  public Attr process(org.w3c.dom.Document basedoc, Format format, Attribute attribute) {
    return printAttribute(basedoc, attribute);
  }

  /* *******************************************
   * Support methods for output. Should all be protected. All content-type
   * print methods have a FormatStack. Only printContent is responsible for
   * outputting appropriate indenting and newlines, which are easily available
   * using the FormatStack.getLevelIndent() and FormatStack.getLevelEOL().
   * *******************************************
   */

  /**
   * This will handle printing of a {@link Document}.
   *
   * @param fstack  the FormatStack
   * @param nstack  the NamespaceStack
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param doc     <code>Document</code> to write.
   * @return The input JDOM document converted to a DOM document.
   */
  protected org.w3c.dom.Document printDocument(final FormatStack fstack,
                                               final NamespaceStack nstack, final org.w3c.dom.Document basedoc,
                                               final Document doc) {

    if (!fstack.isOmitDeclaration()) {
      basedoc.setXmlVersion("1.0");
    }

    final int sz = doc.getContentSize();

    if (sz > 0) {
      for (int i = 0; i < sz; i++) {
        final Content c = doc.getContent(i);
        Node n = null;
        switch (c.getCType()) {
          case Comment:
            n = printComment(basedoc, (Comment)c);
            break;
          case DocType:
            // cannot simply add a DocType to a DOM object
            // it is added when the DOM Document is created.
            // leave n as null
            break;
          case Element:
            n = printElement(fstack, nstack, basedoc, (Element)c);
            break;
          case ProcessingInstruction:
            n = printProcessingInstruction(basedoc,
                                           (ProcessingInstruction)c);
            break;
          default:
            // do nothing.
        }
        if (n != null) {
          basedoc.appendChild(n);
        }
      }
    }

    return basedoc;
  }

  /**
   * This will handle printing of a {@link ProcessingInstruction}.
   *
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param pi      <code>ProcessingInstruction</code> to write.
   * @return The input JDOM ProcessingInstruction converted to a DOM
   * ProcessingInstruction.
   */
  private static org.w3c.dom.ProcessingInstruction printProcessingInstruction(
    final org.w3c.dom.Document basedoc,
    final ProcessingInstruction pi) {
    String target = pi.getTarget();
    String rawData = pi.getData();
    if (rawData == null || rawData.trim().length() == 0) {
      rawData = "";
    }
    return basedoc.createProcessingInstruction(target, rawData);
  }

  /**
   * This will handle printing of a {@link Comment}.
   *
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param comment <code>Comment</code> to write.
   * @return The input JDOM Comment converted to a DOM Comment
   */
  private static org.w3c.dom.Comment printComment(final org.w3c.dom.Document basedoc, final Comment comment) {
    return basedoc.createComment(comment.getText());
  }

  /**
   * This will handle printing of an {@link EntityRef}.
   *
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param entity  <code>EntotyRef</code> to write.
   * @return The input JDOM EntityRef converted to a DOM EntityReference
   */
  private static EntityReference printEntityRef(
    final org.w3c.dom.Document basedoc,
    final EntityRef entity) {
    return basedoc.createEntityReference(entity.getName());
  }

  /**
   * This will handle printing of a {@link CDATA}.
   *
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param cdata   <code>CDATA</code> to write.
   * @return The input JDOM CDATA converted to a DOM CDATASection
   */
  private static CDATASection printCDATA(final org.w3c.dom.Document basedoc, final CDATA cdata) {
    // CDATAs are treated like text, not indented/newline content.
    return basedoc.createCDATASection(cdata.getText());
  }

  /**
   * This will handle printing of a {@link Text}.
   *
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param text    <code>Text</code> to write.
   * @return The input JDOM Text converted to a DOM Text
   */
  protected org.w3c.dom.Text printText(final org.w3c.dom.Document basedoc, final Text text) {
    return basedoc.createTextNode(text.getText());
  }

  /**
   * This will handle printing of a {@link Attribute}.
   *
   * @param basedoc   The org.w3c.dom.Document for creating DOM Nodes
   * @param attribute <code>Attribute</code> to write.
   * @return The input JDOM Attribute converted to a DOM Attr
   */
  private static Attr printAttribute(org.w3c.dom.Document basedoc, Attribute attribute) {
    Attr attr = basedoc.createAttributeNS(attribute.getNamespaceURI(), attribute.getQualifiedName());
    attr.setValue(attribute.getValue());
    return attr;
  }

  /**
   * This will handle printing of an {@link Element}.
   * <p>
   * This method arranges for outputting the Element infrastructure including
   * Namespace Declarations and Attributes.
   * <p>
   * The actual formatting of the content is managed by the Walker created for
   * the Element's content.
   * <p>
   *
   * @param fstack  the FormatStack
   * @param nstack  the NamespaceStack
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param element <code>Element</code> to write.
   * @return The input JDOM Element converted to a DOM Element
   */
  private org.w3c.dom.Element printElement(final FormatStack fstack,
                                           final NamespaceStack nstack, final org.w3c.dom.Document basedoc,
                                           final Element element) {

    nstack.push(element);
    try {

      TextMode textmode = fstack.getTextMode();

      // Check for xml:space and adjust format settings
      final String space = element.getAttributeValue("space",
                                                     Namespace.XML_NAMESPACE);

      if ("default".equals(space)) {
        textmode = fstack.getDefaultMode();
      }
      else if ("preserve".equals(space)) {
        textmode = TextMode.PRESERVE;
      }

      org.w3c.dom.Element ret = basedoc.createElementNS(
        element.getNamespaceURI(), element.getQualifiedName());

      for (Namespace ns : nstack.addedForward()) {
        if (ns == Namespace.XML_NAMESPACE) {
          continue;
        }
        ret.setAttributeNS(JDOMConstants.NS_URI_XMLNS, getXmlnsTagFor(ns), ns.getURI());
      }

      if (element.hasAttributes()) {
        for (Attribute att : element.getAttributes()) {
          Attr a = printAttribute(basedoc, att);
          ret.setAttributeNodeNS(a);
        }
      }

      final List<Content> content = element.getContent();

      if (!content.isEmpty()) {
        fstack.push();
        try {
          fstack.setTextMode(textmode);
          Walker walker = buildWalker(fstack, content, false);

          if (!walker.isAllText() && fstack.getPadBetween() != null) {
            // we need to newline/indent
            final org.w3c.dom.Text n = basedoc.createTextNode(
              fstack.getPadBetween());
            ret.appendChild(n);
          }

          printContent(fstack, nstack, basedoc, ret, walker);

          if (!walker.isAllText() && fstack.getPadLast() != null) {
            // we need to newline/indent
            final org.w3c.dom.Text n = basedoc.createTextNode(
              fstack.getPadLast());
            ret.appendChild(n);
          }
        }
        finally {
          fstack.pop();
        }
      }

      return ret;
    }
    finally {
      nstack.pop();
    }
  }

  /**
   * This will handle printing of a List of {@link Content}. Uses the Walker
   * to ensure formatting.
   *
   * @param fstack  the FormatStack
   * @param nstack  the NamespaceStack
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param target  the DOM node this content should be appended to.
   * @param walker  <code>List</code> of <code>Content</code> to write.
   */
  private void printContent(final FormatStack fstack,
                            final NamespaceStack nstack, final org.w3c.dom.Document basedoc,
                            final Node target, final Walker walker) {

    while (walker.hasNext()) {
      final Content c = walker.next();
      Node n;
      if (c == null) {
        // Formatted Text or CDATA
        final String text = walker.text();
        if (walker.isCDATA()) {
          n = printCDATA(basedoc, new CDATA(text));
        }
        else {
          n = printText(basedoc, new Text(text));
        }
      }
      else {
        n = helperContentDispatcher(fstack, nstack,
                                    basedoc, c);
      }
      if (n != null) {
        target.appendChild(n);
      }
    }
  }

  /**
   * This method contains code which is reused in a number of places. It
   * simply determines what content is passed in, and dispatches it to the
   * correct print* method.
   *
   * @param fstack  The current FormatStack
   * @param nstack  the NamespaceStack
   * @param basedoc The org.w3c.dom.Document for creating DOM Nodes
   * @param content The content to dispatch
   * @return the input JDOM Content converted to a DOM Node.
   */
  private Node helperContentDispatcher(FormatStack fstack, NamespaceStack nstack, org.w3c.dom.Document basedoc, Content content) {
    switch (content.getCType()) {
      case CDATA:
        return printCDATA(basedoc, (CDATA)content);
      case Comment:
        return printComment(basedoc, (Comment)content);
      case Element:
        return printElement(fstack, nstack, basedoc, (Element)content);
      case EntityRef:
        return printEntityRef(basedoc, (EntityRef)content);
      case ProcessingInstruction:
        return printProcessingInstruction(basedoc,
                                          (ProcessingInstruction)content);
      case Text:
        return printText(basedoc, (Text)content);
      case DocType:
        return null;
      default:
        throw new IllegalStateException("Unexpected Content "
                                        + content.getCType());
    }
  }
}
