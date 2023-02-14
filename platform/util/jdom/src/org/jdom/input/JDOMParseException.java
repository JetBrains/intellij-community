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

package org.jdom.input;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.xml.sax.SAXParseException;

/**
 * Thrown during parse errors, with information about where the parse error
 * occurred as well as access to the partially built document.
 *
 * @author Laurent Bihanic
 */
public final class JDOMParseException extends JDOMException {
  /**
   * The portion of the document that was successfully built before
   * the parse error occurred.
   */
  private final Document partialDocument;

  /**
   * This will create a parse <code>Exception</code> with the given
   * message and the partial document and wrap the
   * <code>Exception</code> that cause a document parse to fail.
   *
   * @param message         <code>String</code> message indicating
   *                        the problem that occurred.
   * @param cause           <code>Throwable</code> that caused this
   *                        to be thrown.
   * @param partialDocument <code>Document</code> the portion of
   *                        the input XML document that was
   *                        successfully built.
   */
  public JDOMParseException(String message, Throwable cause,
                            Document partialDocument) {
    super(message, cause);
    this.partialDocument = partialDocument;
  }

  /**
   * Returns the partial document that was successfully built before
   * the error occurred.
   *
   * @return the partial document or null if none.
   */
  public Document getPartialDocument() {
    return partialDocument;
  }

  /**
   * Returns the public identifier of the entity where the
   * parse error occurred.
   *
   * @return a string containing the public identifier, or
   * <code>null</code> if the information is not available.
   */
  public String getPublicId() {
    return (getCause() instanceof SAXParseException) ?
           ((SAXParseException)getCause()).getPublicId() : null;
  }

  /**
   * Returns the system identifier of the entity where the
   * parse error occurred.
   *
   * @return a string containing the system identifier, or
   * <code>null</code> if the information is not available.
   */
  public String getSystemId() {
    return (getCause() instanceof SAXParseException) ?
           ((SAXParseException)getCause()).getSystemId() : null;
  }

  /**
   * Returns the line number of the end of the text where the
   * parse error occurred.
   * <p>
   * The first line in the document is line 1.</p>
   *
   * @return an integer representing the line number, or -1
   * if the information is not available.
   */
  public int getLineNumber() {
    return (getCause() instanceof SAXParseException) ?
           ((SAXParseException)getCause()).getLineNumber() : -1;
  }

  /**
   * Returns the column number of the end of the text where the
   * parse error occurred.
   * <p>
   * The first column in a line is position 1.</p>
   *
   * @return an integer representing the column number, or -1
   * if the information is not available.
   */
  public int getColumnNumber() {
    return (getCause() instanceof SAXParseException) ?
           ((SAXParseException)getCause()).getColumnNumber() : -1;
  }
}

