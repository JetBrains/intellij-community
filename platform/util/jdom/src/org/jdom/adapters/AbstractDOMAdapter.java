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

package org.jdom.adapters;

import org.jdom.DocType;
import org.jdom.JDOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A DOMAdapter utility abstract base class. Uses the concrete implementation
 * to build an org.w3c.dom.Document instance, which in turn is used to apply
 * the DocType.
 * <p>
 * Special attention should be paid to the setInternalSubset protected method,
 * which may, or may not be supported by your actual DOM implementation.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 */
public abstract class AbstractDOMAdapter implements DOMAdapter {

  /**
   * This creates an empty <code>Document</code> object based
   * on a specific parser implementation with the given DOCTYPE.
   * If the doctype parameter is null, the behavior is the same as
   * calling <code>createDocument()</code>.
   *
   * @param doctype Initial <code>DocType</code> of the document.
   * @return <code>Document</code> - created DOM Document.
   * @throws JDOMException when errors occur.
   */
  @Override
  public Document createDocument(DocType doctype) throws JDOMException {
    if (doctype == null) {
      return createDocument();
    }

    DOMImplementation domImpl = createDocument().getImplementation();
    DocumentType domDocType = domImpl.createDocumentType(
      doctype.getElementName(),
      doctype.getPublicID(),
      doctype.getSystemID());

    // Set the internal subset if possible
    setInternalSubset(domDocType, doctype.getInternalSubset());

    Document ret = domImpl.createDocument("http://temporary",
                                          doctype.getElementName(),
                                          domDocType);

    Element root = ret.getDocumentElement();
    if (root != null) {
      ret.removeChild(root);
    }

    return ret;
  }

  /**
   * This attempts to change the DocumentType to have the given internal DTD
   * subset value.  This is not a standard ability in DOM, so it's only
   * available with some parsers.  Subclasses can alter the mechanism by
   * which the attempt is made to set the value.
   *
   * @param dt DocumentType to be altered
   * @param s  String to use as the internal DTD subset
   */
  private static void setInternalSubset(DocumentType dt, String s) {
    if (dt == null || s == null) return;

    // Default behavior is to attempt a setInternalSubset() call using
    // reflection.  This method is not part of the DOM spec, but it's
    // available on Xerces 1.4.4+.  It's not currently in Crimson.
    try {
      Class<? extends DocumentType> dtclass = dt.getClass();
      Method setInternalSubset = dtclass.getMethod(
        "setInternalSubset", String.class);
      setInternalSubset.invoke(dt, s);
    }
    catch (InvocationTargetException | IllegalAccessException | SecurityException | NoSuchMethodException e) {
      // ignore
    }
  }
}
