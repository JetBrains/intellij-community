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
import org.jdom.output.DOMOutputter;
import org.w3c.dom.Document;

/**
 * Defines a standard set of adapter methods for interfacing with a DOM parser
 * and obtaining a DOM {@link Document org.w3c.dom.Document} object.
 * Instances of this interface are used by the {@link DOMOutputter} class to
 * create a DOM output result using the org.w3c.dom.Document implementation
 * returned by these methods.
 * <p>
 * You should never need to implement this interface unless you have a specific
 * need to use something other than the default JAXP-based mechanism.
 * <p>
 * The {@link AbstractDOMAdapter} class could help you by implementing the
 * DocType-based method which leverages the base createDocument() method.
 * <p>
 * <strong>Special note for implementation of DOMAdapter</strong>: For backward
 * compatibility with JDOM 1.x (which allows a class-name to be used to specify
 * a DOMAdapter in the DOMOoutputter class), it is required that your
 * implementations of DOMAdapter have a no-argument default constructor. If you
 * require a constructor argument then you have to ensure that you use the
 * correct (non-deprecated) mechanisms on DOMOutputter to specify your custom
 * DOMAdapter.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 */
public interface DOMAdapter {

  /**
   * This creates an empty <code>Document</code> object based
   * on a specific parser implementation.
   *
   * @return <code>Document</code> - created DOM Document.
   * @throws JDOMException if an error occurs.
   */
  Document createDocument() throws JDOMException;

  /**
   * This creates an empty <code>Document</code> object based
   * on a specific parser implementation with the given DOCTYPE.
   *
   * @param doctype Initial <code>DocType</code> of the document.
   * @return <code>Document</code> - created DOM Document.
   * @throws JDOMException if an error occurs.
   */
  Document createDocument(DocType doctype) throws JDOMException;
}
