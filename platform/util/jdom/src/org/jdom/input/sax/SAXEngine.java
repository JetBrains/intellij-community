/*--

 Copyright (C) 2011 Jason Hunter & Brett McLaughlin.
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

package org.jdom.input.sax;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;

/**
 * SAXEngine provides an interface to interact with either the SAXBuilder or the
 * SAXBuilderEngine. This makes it possible to do pooling of SAXEngines for
 * parsing using instances of either SAXBuilder or SAXBuilderEngine.
 *
 * @author Rolf Lear
 * @see org.jdom.input.sax
 */
public interface SAXEngine {
  /**
   * Returns whether validation is to be performed during the build.
   *
   * @return whether validation is to be performed during the build
   */
  boolean isValidating();

  /**
   * Returns whether element content whitespace is to be ignored during the
   * build.
   *
   * @return whether element content whitespace is to be ignored during the
   * build
   */
  boolean getIgnoringElementContentWhitespace();

  /**
   * Returns whether or not the parser will elminate element content
   * containing only whitespace.
   *
   * @return <code>boolean</code> - whether only whitespace content will be
   * ignored during build.
   */
  boolean getIgnoringBoundaryWhitespace();

  /**
   * Returns whether or not entities are being expanded into normal text
   * content.
   *
   * @return whether entities are being expanded
   */
  boolean getExpandEntities();

  /**
   * This builds a document from the supplied input source.
   *
   * @param in <code>InputSource</code> to read from
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(InputSource in) throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied input stream.
   * </p>
   *
   * @param in <code>InputStream</code> to read from
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed.
   */
  Document build(InputStream in) throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied filename.
   * </p>
   *
   * @param file <code>File</code> to read from
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(final File file) throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied URL.
   * </p>
   *
   * @param url <code>URL</code> to read from.
   * @return <code>Document</code> - resultant Document object.
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed.
   */
  Document build(final URL url) throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied input stream.
   * </p>
   *
   * @param in       <code>InputStream</code> to read from.
   * @param systemId base for resolving relative URIs
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(final InputStream in, final String systemId)
    throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied Reader. It's the programmer's
   * responsibility to make sure the reader matches the encoding of the file.
   * It's often easier and safer to use an InputStream rather than a Reader,
   * and to let the parser auto-detect the encoding from the XML declaration.
   * </p>
   *
   * @param characterStream <code>Reader</code> to read from
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(final Reader characterStream)
    throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied Reader. It's the programmer's
   * responsibility to make sure the reader matches the encoding of the file.
   * It's often easier and safer to use an InputStream rather than a Reader,
   * and to let the parser auto-detect the encoding from the XML declaration.
   * </p>
   *
   * @param characterStream <code>Reader</code> to read from.
   * @param systemId        base for resolving relative URIs
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(final Reader characterStream, final String systemId)
    throws JDOMException, IOException;

  /**
   * <p>
   * This builds a document from the supplied URI.
   * </p>
   *
   * @param systemId URI for the input
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  Document build(final String systemId)
    throws JDOMException, IOException;
}