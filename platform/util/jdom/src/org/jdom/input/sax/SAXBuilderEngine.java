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
import org.jdom.input.JDOMParseException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Builds a JDOM document from files, streams, readers, URLs, or a SAX
 * {@link InputSource} instance using a SAX parser. This Engine is
 * built by the SAXBuilder based on the state of the SAXBuilder when the engine
 * was produced. It is not possible to reconfigure the engine once built, but it
 * can be reused many times (though not concurrently). This makes it the fastest
 * way to process many multitudes of XML documents (if those documents are all
 * parsed the same way). If you want to process in multiple threads you can
 * safely have one SAXBuilderEngine in each thread on the condition that:
 * <ol>
 * <li>The JDOMFactory is thread-safe (the JDOM-supplied JDOMFactories are)
 * <li>There is no XMLFilter given to the SAXBuilder, or, if there is, then it
 * is thread-safe.
 * <li>If you have a custom {@link XMLReaderJDOMFactory} that it supplies a new
 * instance of an XMLReader on each call (the JDOM-supplied ones all do).
 * <li>If you have a custom {@link SAXHandlerFactory} that it supplies a new
 * instance of a SAXHandler on each call (the JDOM-supplied one does)
 * </ol>
 *
 * @author Rolf Lear
 * @see org.jdom.input.sax
 */
public final class SAXBuilderEngine implements SAXEngine {
  /**
   * The SAX XMLReader.
   */
  private final XMLReader saxParser;

  /**
   * The SAXHandler
   */
  private final SAXHandler saxHandler;

  /**
   * indicates whether this is a validating parser
   */
  private final boolean validating;

  /**
   * Creates a new SAXBuilderEngine.
   *
   * @param reader     The XMLReader this Engine parses with
   * @param handler    The SAXHandler that processes the SAX Events.
   * @param validating True if this is a validating system.
   */
  public SAXBuilderEngine(XMLReader reader, final SAXHandler handler, boolean validating) {
    saxParser = reader;
    saxHandler = handler;
    this.validating = validating;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#isValidating()
   */
  @Override
  public boolean isValidating() {
    return validating;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#isIgnoringElementContentWhitespace()
   */
  @Override
  public boolean getIgnoringElementContentWhitespace() {
    return saxHandler.getIgnoringElementContentWhitespace();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#isIgnoringBoundaryWhitespace()
   */
  @Override
  public boolean getIgnoringBoundaryWhitespace() {
    return saxHandler.getIgnoringBoundaryWhitespace();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#isExpandEntities()
   */
  @Override
  public boolean getExpandEntities() {
    return saxHandler.getExpandEntities();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(org.xml.sax.InputSource)
   */
  @Override
  public Document build(final InputSource in)
    throws JDOMException, IOException {
    try {
      // Parse the document.
      saxParser.parse(in);

      return saxHandler.getDocument();
    }
    catch (final SAXParseException e) {
      Document doc = saxHandler.getDocument();
      if (!doc.hasRootElement()) {
        doc = null;
      }

      final String systemId = e.getSystemId();
      if (systemId != null) {
        throw new JDOMParseException("Error on line " +
                                     e.getLineNumber() + " of document " + systemId + ": " +
                                     e.getMessage(), e, doc);
      }
      throw new JDOMParseException("Error on line " +
                                   e.getLineNumber() + ": " +
                                   e.getMessage(), e, doc);
    }
    catch (final SAXException e) {
      throw new JDOMParseException("Error in building: " +
                                   e.getMessage(), e, saxHandler.getDocument());
    }
    finally {
      // Explicitly nullify the handler to encourage GC
      // It's a stack var so this shouldn't be necessary, but it
      // seems to help on some JVMs
      saxHandler.reset();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.io.InputStream)
   */
  @Override
  public Document build(final InputStream in) throws JDOMException, IOException {
    return build(new InputSource(in));
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.io.File)
   */
  @Override
  public Document build(final File file) throws JDOMException, IOException {
    try {
      return build(fileToURL(file));
    }
    catch (final MalformedURLException e) {
      throw new JDOMException("Error in building", e);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.net.URL)
   */
  @Override
  public Document build(final URL url) throws JDOMException, IOException {
    return build(new InputSource(url.toExternalForm()));
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.io.InputStream,
   * java.lang.String)
   */
  @Override
  public Document build(final InputStream in, final String systemId)
    throws JDOMException, IOException {

    final InputSource src = new InputSource(in);
    src.setSystemId(systemId);
    return build(src);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.io.Reader)
   */
  @Override
  public Document build(final Reader characterStream)
    throws JDOMException, IOException {
    return build(new InputSource(characterStream));
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.io.Reader,
   * java.lang.String)
   */
  @Override
  public Document build(final Reader characterStream, final String systemId)
    throws JDOMException, IOException {

    final InputSource src = new InputSource(characterStream);
    src.setSystemId(systemId);
    return build(src);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.input.sax.SAXEngine#build(java.lang.String)
   */
  @Override
  public Document build(final String systemId)
    throws JDOMException, IOException {
    return build(new InputSource(systemId));
  }

  /**
   * Custom File.toUrl() implementation to handle special chars in file names
   * Actually, we can now rely on the core Java toURI function,
   *
   * @param file file object whose path will be converted
   * @return URL form of the file, with special characters handled
   * @throws MalformedURLException if there's a problem constructing a URL
   */
  private static URL fileToURL(final File file) throws MalformedURLException {

    return file.getAbsoluteFile().toURI().toURL();
  }
}
