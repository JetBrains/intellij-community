/*--

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom.xpath.jaxen;

import org.jaxen.*;
import org.jaxen.saxpath.SAXPathException;
import org.jaxen.util.SingleObjectIterator;
import org.jdom.*;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

class JDOMCoreNavigator extends DefaultNavigator {
  private transient IdentityHashMap<Element, NamespaceContainer[]> emtnsmap = new IdentityHashMap<>();

  void reset() {
    emtnsmap.clear();
  }


  @Override
  public final XPath parseXPath(String path) throws SAXPathException {
    return new BaseXPath(path, this);
  }

  @Override
  public final Object getDocument(String url) throws FunctionCallException {
    SAXBuilder sb = new SAXBuilder();
    try {
      return sb.build(url);
    }
    catch (JDOMException e) {
      throw new FunctionCallException("Failed to parse " + url, e);
    }
    catch (IOException e) {
      throw new FunctionCallException("Failed to access " + url, e);
    }
  }

  @Override
  public final boolean isText(Object isit) {
    return isit instanceof Text;
  }

  @Override
  public final boolean isProcessingInstruction(Object isit) {
    return isit instanceof ProcessingInstruction;
  }

  @Override
  public final boolean isNamespace(Object isit) {
    return (isit instanceof NamespaceContainer);
  }

  @Override
  public final boolean isElement(Object isit) {
    return isit instanceof Element;
  }

  @Override
  public final boolean isDocument(Object isit) {
    return isit instanceof Document;
  }

  @Override
  public final boolean isComment(Object isit) {
    return isit instanceof Comment;
  }

  @Override
  public final boolean isAttribute(Object isit) {
    return isit instanceof Attribute;
  }

  @Override
  public final String getTextStringValue(Object text) {
    // CDATA is a subclass of Text
    return ((Text)text).getText();
  }

  @Override
  public final String getNamespaceStringValue(Object namespace) {
    return ((NamespaceContainer)namespace).getNamespace().getURI();
  }

  @Override
  public final String getNamespacePrefix(Object namespace) {
    return ((NamespaceContainer)namespace).getNamespace().getPrefix();
  }

  private void recurseElementText(Element element, StringBuilder sb) {
    for (Iterator<?> it = element.getContent().iterator(); it.hasNext(); ) {
      Content c = (Content)it.next();
      if (c instanceof Element) {
        recurseElementText((Element)c, sb);
      }
      else if (c instanceof Text) {
        sb.append(((Text)c).getText());
      }
    }
  }

  @Override
  public final String getElementStringValue(Object element) {
    StringBuilder sb = new StringBuilder();
    recurseElementText((Element)element, sb);
    return sb.toString();
  }

  @Override
  public final String getElementQName(Object element) {
    Element e = (Element)element;
    if (e.getNamespace().getPrefix().length() == 0) {
      return e.getName();
    }
    return e.getNamespacePrefix() + ":" + e.getName();
  }

  @Override
  public final String getElementNamespaceUri(Object element) {
    return ((Element)element).getNamespaceURI();
  }

  @Override
  public final String getElementName(Object element) {
    return ((Element)element).getName();
  }

  @Override
  public final String getCommentStringValue(Object comment) {
    return ((Comment)comment).getValue();
  }

  @Override
  public final String getAttributeStringValue(Object attribute) {
    return ((Attribute)attribute).getValue();
  }

  @Override
  public final String getAttributeQName(Object att) {
    Attribute attribute = (Attribute)att;
    if (attribute.getNamespacePrefix().length() == 0) {
      return attribute.getName();
    }
    return attribute.getNamespacePrefix() + ":" + attribute.getName();
  }

  @Override
  public final String getAttributeNamespaceUri(Object attribute) {
    return ((Attribute)attribute).getNamespaceURI();
  }

  @Override
  public final String getAttributeName(Object attribute) {
    return ((Attribute)attribute).getName();
  }

  @Override
  public final String getProcessingInstructionTarget(Object pi) {
    return ((ProcessingInstruction)pi).getTarget();
  }

  @Override
  public final String getProcessingInstructionData(Object pi) {
    return ((ProcessingInstruction)pi).getData();
  }

  @Override
  public final Object getDocumentNode(Object contextNode) {
    if (contextNode instanceof Document) {
      return contextNode;
    }
    if (contextNode instanceof NamespaceContainer) {
      return ((NamespaceContainer)contextNode).getParentElement().getDocument();
    }
    if (contextNode instanceof Attribute) {
      return ((Attribute)contextNode).getDocument();
    }
    return ((Content)contextNode).getDocument();
  }

  @Override
  public final Object getParentNode(Object contextNode) throws UnsupportedAxisException {
    if (contextNode instanceof Document) {
      return null;
    }
    if (contextNode instanceof NamespaceContainer) {
      return ((NamespaceContainer)contextNode).getParentElement();
    }
    if (contextNode instanceof Content) {
      return ((Content)contextNode).getParent();
    }
    if (contextNode instanceof Attribute) {
      return ((Attribute)contextNode).getParent();
    }
    return null;
  }

  @Override
  public final Iterator<?> getAttributeAxisIterator(Object contextNode) throws UnsupportedAxisException {
    if (isElement(contextNode) && ((Element)contextNode).hasAttributes()) {
      return ((Element)contextNode).getAttributes().iterator();
    }
    return JaxenConstants.EMPTY_ITERATOR;
  }

  @Override
  public final Iterator<?> getChildAxisIterator(Object contextNode) throws UnsupportedAxisException {
    if (contextNode instanceof Parent) {
      return ((Parent)contextNode).getContent().iterator();
    }
    return JaxenConstants.EMPTY_ITERATOR;
  }

  @Override
  public final Iterator<?> getNamespaceAxisIterator(final Object contextNode) throws UnsupportedAxisException {
    //The namespace axis applies to Elements only in XPath.
    if (!isElement(contextNode)) {
      return JaxenConstants.EMPTY_ITERATOR;
    }
    NamespaceContainer[] ret = emtnsmap.get(contextNode);
    if (ret == null) {
      List<Namespace> nsl = ((Element)contextNode).getNamespacesInScope();
      ret = new NamespaceContainer[nsl.size()];
      int i = 0;
      for (Namespace ns : nsl) {
        ret[i++] = new NamespaceContainer(ns, (Element)contextNode);
      }
      emtnsmap.put((Element)contextNode, ret);
    }

    return Arrays.asList(ret).iterator();
  }

  @Override
  public final Iterator<?> getParentAxisIterator(Object contextNode) throws UnsupportedAxisException {

    Parent p = null;
    if (contextNode instanceof Content) {
      p = ((Content)contextNode).getParent();
    }
    else if (contextNode instanceof NamespaceContainer) {
      p = ((NamespaceContainer)contextNode).getParentElement();
    }
    else if (contextNode instanceof Attribute) {
      p = ((Attribute)contextNode).getParent();
    }
    if (p != null) {
      return new SingleObjectIterator(p);
    }
    return JaxenConstants.EMPTY_ITERATOR;
  }

  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    emtnsmap = new IdentityHashMap<>();
  }

  private void writeObject(ObjectOutputStream out)
    throws IOException {
    out.defaultWriteObject();
  }
}