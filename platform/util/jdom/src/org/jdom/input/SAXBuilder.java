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

import org.jdom.*;
import org.jdom.input.sax.*;
import org.xml.sax.*;

import javax.xml.XMLConstants;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.jdom.JDOMConstants.*;

/**
 * @deprecated Please JDOMUtil or JDK API (StAX) or XmlDomReader.readXmlAsModel.
 */
@Deprecated
public final class SAXBuilder implements SAXEngine {

  /**
   * Default source of SAXHandlers
   */
  private static final SAXHandlerFactory DEFAULTSAXHANDLERFAC = new DefaultSAXHandlerFactory();

  private static final JDOMFactory DEFAULT_JDOM_FACTORY = new DefaultJDOMFactory();

  private XMLReaderJDOMFactory readerFactory;

  /**
   * The SAXHandler pillar of SAXBuilder
   */
  private SAXHandlerFactory handlerfac;

  /**
   * The JDOMFactory pillar for creating new JDOM objects
   */
  private JDOMFactory jdomFactory = DEFAULT_JDOM_FACTORY;

  /**
   * User-specified features to be set on the SAX parser
   */
  private final HashMap<String, Boolean> features = new HashMap<>(5);

  /**
   * User-specified properties to be set on the SAX parser
   */
  private final HashMap<String, Object> properties = new HashMap<>(5);

  /**
   * EntityResolver class to use
   */
  private EntityResolver saxEntityResolver = null;

  /**
   * Whether expansion of entities should occur
   */
  private boolean expand = true;

  /**
   * Whether to ignore ignorable whitespace
   */
  private boolean ignoringWhite = false;

  /**
   * Whether to ignore all whitespace content
   */
  private boolean ignoringBoundaryWhite = false;

  /**
   * The current SAX parser, if parser reuse has been activated.
   */
  private SAXEngine engine = null;

  public SAXBuilder() {
    this(XMLReaders.NONVALIDATING, null);
  }

  /**
   * @deprecated use {@link SAXBuilder#SAXBuilder(XMLReaderJDOMFactory)} with
   * either {@link XMLReaders#DTDVALIDATING}
   * or {@link XMLReaders#NONVALIDATING}
   */
  @Deprecated
  public SAXBuilder(final boolean validate) {
    this(validate ? XMLReaders.DTDVALIDATING : XMLReaders.NONVALIDATING);
  }

  /**
   * Creates a new SAXBuilder with the specified XMLReaderJDOMFactory.
   * <p>
   *
   * @param factory the {@link XMLReaderJDOMFactory} that supplies XMLReaders. If the
   *                    value is null then a Non-Validating JAXP-based SAX2.0 parser will
   *                    be used.
   * @see XMLReaderJDOMFactory
   * @see XMLReaders#NONVALIDATING
   * @see DefaultJDOMFactory
   * @see org.jdom.input.sax for important details on SAXBuilder
   */
  public SAXBuilder(XMLReaderJDOMFactory factory) {
    this(factory == null ? XMLReaders.NONVALIDATING : factory, null);
  }

  private SAXBuilder(XMLReaderJDOMFactory factory, SAXHandlerFactory handlerFactory) {
    this.readerFactory = factory;
    this.handlerfac = handlerFactory == null ? DEFAULTSAXHANDLERFAC : handlerFactory;
  }

  /**
   * This sets a custom JDOMFactory for the builder. Use this to build the
   * tree with your own subclasses of the JDOM classes.
   *
   * @param factory <code>JDOMFactory</code> to use
   * @deprecated as it is replaced by {@link #setJDOMFactory(JDOMFactory)}
   */
  @Deprecated
  public void setFactory(final JDOMFactory factory) {
    setJDOMFactory(factory);
  }

  /**
   * This sets a custom JDOMFactory for the builder. Use this to build the
   * tree with your own subclasses of the JDOM classes.
   *
   * @param factory <code>JDOMFactory</code> to use
   */
  public void setJDOMFactory(final JDOMFactory factory) {
    this.jdomFactory = factory;
    engine = null;
  }

  /**
   * Set the current XMLReader factory.
   *
   * @param rfac the JDOMXMLReaderFactory to set. A null rfac will indicate the default {@link XMLReaders#NONVALIDATING}
   */
  public void setXMLReaderFactory(XMLReaderJDOMFactory rfac) {
    readerFactory = rfac == null ? XMLReaders.NONVALIDATING : rfac;
    engine = null;
  }

  /**
   * Set the SAXHandlerFactory to be used by this SAXBuilder.
   *
   * @param factory the required SAXHandlerFactory.
   */
  @SuppressWarnings("unused")
  public void setSAXHandlerFactory(final SAXHandlerFactory factory) {
    this.handlerfac = factory == null ? DEFAULTSAXHANDLERFAC : factory;
    engine = null;
  }

  /**
   * Returns whether validation is to be performed during the build.
   *
   * @return whether validation is to be performed during the build
   * @deprecated in lieu of {@link #isValidating()}
   */
  @Deprecated
  public boolean getValidation() {
    return isValidating();
  }

  /**
   * Returns whether validation is to be performed during the build.
   *
   * @return whether validation is to be performed during the build
   */
  @Override
  public boolean isValidating() {
    return readerFactory.isValidating();
  }

  /**
   * @deprecated use {@link #setXMLReaderFactory(XMLReaderJDOMFactory)}
   */
  @Deprecated
  public void setValidation(final boolean validate) {
    setXMLReaderFactory(validate ? XMLReaders.DTDVALIDATING : XMLReaders.NONVALIDATING);
  }

  /**
   * This sets custom EntityResolver for the <code>Builder</code>.
   *
   * @param entityResolver <code>EntityResolver</code>
   */
  public void setEntityResolver(final EntityResolver entityResolver) {
    saxEntityResolver = entityResolver;
    engine = null;
  }

  /**
   * Returns whether element content whitespace is to be ignored during the
   * build.
   *
   * @return whether element content whitespace is to be ignored during the
   * build
   */
  @Override
  public boolean getIgnoringElementContentWhitespace() {
    return ignoringWhite;
  }

  /**
   * Specifies whether or not the parser should eliminate whitespace in
   * element content (sometimes known as "ignorable whitespace") when building
   * the document. Only whitespace which is contained within element content
   * that has an element only content model will be eliminated (see XML Rec
   * 3.2.1). For this setting to take effect requires that validation be
   * turned on. The default value of this setting is <code>false</code>.
   *
   * @param ignoringWhite Whether to ignore ignorable whitespace
   */
  public void setIgnoringElementContentWhitespace(final boolean ignoringWhite) {
    this.ignoringWhite = ignoringWhite;
    engine = null;
  }

  /**
   * Returns whether or not the parser will eliminate element content
   * containing only whitespace.
   *
   * @return <code>boolean</code> - whether only whitespace content will be
   * ignored during build.
   * @see #setIgnoringBoundaryWhitespace
   */
  @Override
  public boolean getIgnoringBoundaryWhitespace() {
    return ignoringBoundaryWhite;
  }

  /**
   * Specifies whether or not the parser should elminate boundary whitespace,
   * a term that indicates whitespace-only text between element tags. This
   * feature is a lot like
   * {@link #setIgnoringElementContentWhitespace(boolean)} but this feature is
   * more aggressive and doesn't require validation be turned on. The
   * {@link #setIgnoringElementContentWhitespace(boolean)} call impacts the
   * SAX parse process while this method impacts the JDOM build process, so it
   * can be beneficial to turn both on for efficiency. For implementation
   * efficiency, this method actually removes all whitespace-only text()
   * nodes. That can, in some cases (like between an element tag and a
   * comment) include whitespace that isn't just boundary whitespace. The
   * default is <code>false</code>.
   *
   * @param ignoringBoundaryWhite Whether to ignore whitespace-only text nodes
   */
  public void setIgnoringBoundaryWhitespace(final boolean ignoringBoundaryWhite) {
    this.ignoringBoundaryWhite = ignoringBoundaryWhite;
    engine = null;
  }

  /**
   * Returns whether or not entities are being expanded into normal text
   * content.
   *
   * @return whether entities are being expanded
   */
  @Override
  public boolean getExpandEntities() {
    return expand;
  }

  /**
   * <p>
   * This sets whether or not to expand entities for the builder. A true means
   * to expand entities as normal content. A false means to leave entities
   * unexpanded as <code>EntityRef</code> objects. The default is true.
   * </p>
   * <p>
   * When this setting is false, the internal DTD subset is retained; when
   * this setting is true, the internal DTD subset is not retained.
   * </p>
   * <p>
   * Note that Xerces (at least up to 1.4.4) has a bug where entities in
   * attribute values will be incorrectly reported if this flag is turned off,
   * resulting in entities appearing within element content. When turning
   * entity expansion off either avoid entities in attribute values, or use
   * another parser like Crimson.
   * http://nagoya.apache.org/bugzilla/show_bug.cgi?id=6111
   * </p>
   *
   * @param expand <code>boolean</code> indicating whether entity expansion should
   *               occur.
   */
  public void setExpandEntities(final boolean expand) {
    this.expand = expand;
    engine = null;
  }

  /**
   * This sets a feature on the SAX parser. See the SAX documentation for
   * more information. </p>
   * <p>
   * NOTE: SAXBuilder requires that some particular features of the SAX parser
   * be set up in certain ways for it to work properly. The list of such
   * features may change in the future. Therefore, the use of this method may
   * cause parsing to break, and even if it doesn't break anything today it
   * might break parsing in a future JDOM version, because what JDOM parsers
   * require may change over time. Use with caution.
   * </p>
   * JDOM uses {@link XMLReaderJDOMFactory} instances to provide XMLReader
   * instances. If you require special configuration on your XMLReader you
   * should consider extending or implementing an XMLReaderJDOMFactory in the
   * {@link org.jdom.input.sax} package.
   *
   * @param name  The feature name, which is a fully-qualified URI.
   * @param value The requested state of the feature (true or false).
   */
  public void setFeature(final String name, final boolean value) {
    // Save the specified feature for later.
    features.put(name, value ? Boolean.TRUE : Boolean.FALSE);
    engine = null;
  }

  /**
   * This sets a property on the SAX parser. See the SAX documentation for
   * more information.
   * <p>
   * NOTE: SAXBuilder requires that some particular properties of the SAX
   * parser be set up in certain ways for it to work properly. The list of
   * such properties may change in the future. Therefore, the use of this
   * method may cause parsing to break, and even if it doesn't break anything
   * today it might break parsing in a future JDOM version, because what JDOM
   * parsers require may change over time. Use with caution.
   * </p>
   * JDOM uses {@link XMLReaderJDOMFactory} instances to provide XMLReader
   * instances. If you require special configuration on your XMLReader you
   * should consider extending or implementing an XMLReaderJDOMFactory in the
   * {@link org.jdom.input.sax} package.
   *
   * @param name  The property name, which is a fully-qualified URI.
   * @param value The requested value for the property.
   */
  public void setProperty(final String name, final Object value) {
    // Save the specified property for later.
    properties.put(name, value);
    engine = null;
  }

  /**
   * This method builds a new and reusable {@link SAXEngine}.
   * Each time this method is called a new instance of a SAXEngine will be
   * returned.
   * <p>
   * This method is used internally by the various SAXBuilder.build(*) methods
   * (if any configuration has changed) but can also be used as a mechanism
   * for creating SAXEngines to be used in parsing pools or other optimised
   * structures.
   *
   * @return a {@link SAXEngine} representing the current state of the
   * current SAXBuilder settings.
   * @throws JDOMException if there is any problem initialising the engine.
   */
  private SAXEngine buildEngine() throws JDOMException {
    SAXHandler contentHandler = handlerfac.createSAXHandler(jdomFactory);

    contentHandler.setExpandEntities(expand);
    contentHandler.setIgnoringElementContentWhitespace(ignoringWhite);
    contentHandler.setIgnoringBoundaryWhitespace(ignoringBoundaryWhite);

    XMLReader parser = readerFactory.createXMLReader();
    configureParser(parser, contentHandler);
    return new SAXBuilderEngine(parser, contentHandler, readerFactory.isValidating());
  }

  /**
   * This method retrieves (or builds) a SAXBuilderEngine that represents the
   * current SAXBuilder state.
   *
   * @return a {@link SAXBuilderEngine} representing the current state of the
   * current SAXBuilder settings.
   * @throws JDOMException if there is any problem initializing the engine.
   */
  private SAXEngine getEngine() throws JDOMException {
    if (engine != null) {
      return engine;
    }

    engine = buildEngine();
    return engine;
  }

  /**
   * This configures the XMLReader to be used for reading the XML document.
   * <p>
   * The default implementation sets various options on the given XMLReader,
   * such as validation, DTD resolution, entity handlers, etc., according to
   * the options that were set (e.g. via <code>setEntityResolver</code>) and
   * set various SAX properties and features that are required for JDOM
   * internals. These features may change in future releases, so change this
   * behavior at your own risk.
   * </p>
   *
   * @param parser         the XMLReader to configure.
   * @param contentHandler The SAXHandler to use for the XMLReader
   * @throws JDOMException if configuration fails.
   */
  private void configureParser(final XMLReader parser, final SAXHandler contentHandler) throws JDOMException {

    // Setup SAX handlers.

    parser.setContentHandler(contentHandler);

    if (saxEntityResolver != null) {
      parser.setEntityResolver(saxEntityResolver);
    }

    parser.setDTDHandler(contentHandler);

    parser.setErrorHandler(new BuilderErrorHandler());

    boolean success = false;

    try {
      parser.setProperty(SAX_PROPERTY_LEXICAL_HANDLER,
                         contentHandler);
      success = true;
    }
    catch (final SAXNotSupportedException | SAXNotRecognizedException e) {
      // No lexical reporting available
    }

    // Some parsers use alternate property for lexical handling (grr...)
    if (!success) {
      try {
        parser.setProperty(SAX_PROPERTY_LEXICAL_HANDLER_ALT,
                           contentHandler);
      }
      catch (final SAXNotSupportedException | SAXNotRecognizedException e) {
        // No lexical reporting available
      }
    }

    // Set any user-specified properties on the parser.
    for (final Map.Entry<String, Object> me : properties.entrySet()) {
      internalSetProperty(parser, me.getKey(), me.getValue(), me.getKey());
    }

    // Set any user-specified features on the parser (always includes entity expansion true/false).
    for (Map.Entry<String, Boolean> me : features.entrySet()) {
      internalSetFeature(parser, me.getKey(), me.getValue(), me.getKey());
    }

    // Try setting the DeclHandler if entity expansion is off
    if (!getExpandEntities()) {
      try {
        parser.setProperty(SAX_PROPERTY_DECLARATION_HANDLER, contentHandler);
      }
      catch (final SAXNotSupportedException | SAXNotRecognizedException e) {
        // No lexical reporting available
      }
    }

    try {
      parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }
    catch (Exception ignore) {
    }
  }

  /**
   * Tries to set a feature on the parser. If the feature cannot be set,
   * throws a JDOMException describing the problem.
   */
  private static void internalSetFeature(final XMLReader parser, final String feature,
                                         final boolean value, final String displayName) throws JDOMException {
    try {
      parser.setFeature(feature, value);
    }
    catch (final SAXNotSupportedException e) {
      throw new JDOMException(
        displayName + " feature not supported for SAX driver " + parser.getClass().getName());
    }
    catch (final SAXNotRecognizedException e) {
      throw new JDOMException(
        displayName + " feature not recognized for SAX driver " + parser.getClass().getName());
    }
  }

  /**
   * <p>
   * Tries to set a property on the parser. If the property cannot be set,
   * throws a JDOMException describing the problem.
   * </p>
   */
  private static void internalSetProperty(final XMLReader parser, final String property,
                                          final Object value, final String displayName) throws JDOMException {
    try {
      parser.setProperty(property, value);
    }
    catch (final SAXNotSupportedException e) {
      throw new JDOMException(
        displayName + " property not supported for SAX driver " + parser.getClass().getName());
    }
    catch (final SAXNotRecognizedException e) {
      throw new JDOMException(
        displayName + " property not recognized for SAX driver " + parser.getClass().getName());
    }
  }

  /**
   * This builds a document from the supplied input source.
   *
   * @param in <code>InputSource</code> to read from
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  @Override
  public Document build(InputSource in) throws JDOMException, IOException {
    return getEngine().build(in);
  }

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
  @Override
  public Document build(final InputStream in) throws JDOMException, IOException {
    return getEngine().build(in);
  }

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
  @Override
  public Document build(final File file) throws JDOMException, IOException {
    return getEngine().build(file);
  }

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
  @Override
  public Document build(final URL url) throws JDOMException, IOException {
    return getEngine().build(url);
  }

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
  @Override
  public Document build(final InputStream in, final String systemId) throws JDOMException, IOException {
    return getEngine().build(in, systemId);
  }

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
  @Override
  public Document build(final Reader characterStream)
    throws JDOMException, IOException {
    return getEngine().build(characterStream);
  }

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
  @Override
  public Document build(final Reader characterStream, final String systemId)
    throws JDOMException, IOException {
    return getEngine().build(characterStream, systemId);
  }

  /**
   * <p>
   * This builds a document from the supplied URI. The URI is typically a file name, or a URL.
   * Do not use this method for parsing XML content that is in a Java String variable.
   * <p>
   * <ul>
   * <li><Strong>Right:</Strong> <code>....build("path/to/file.xml");</code>
   * <li><Strong>Right:</Strong> <code>....build("http://my.example.com/xmlfile");</code>
   * <li><Strong>Wrong:</Strong> <code>....build("&lt;root>data&lt/root>");</code>
   * </ul>
   * </p>
   * If your XML content is in a Java String variable and you want to parse it, then use:<br/>
   * <code>    ....build(new StringReader("&lt;root>data&lt/root>"));</code>
   * <p>
   *
   * @param systemId URI for the input
   * @return <code>Document</code> resultant Document object
   * @throws JDOMException when errors occur in parsing
   * @throws IOException   when an I/O error prevents a document from being fully parsed
   */
  @Override
  public Document build(final String systemId) throws JDOMException, IOException {
    if (systemId == null) {
      throw new NullPointerException("Unable to build a URI from a null systemID.");
    }

    try {
      return getEngine().build(systemId);
    }
    catch (IOException ioe) {
      // OK, Issue #63
      // it is common for people to pass in raw XML content instead of
      // a SystemID. To make troubleshooting easier, we do a simple check
      // all valid XML documents start with a '<' character.
      // no URI ever has an '<' character.
      // if we think an XML document was passed in, we wrap the exception
      // Typically this problem causes a MalformedURLException to be
      // thrown, but that is not particular specified that way. Of
      // interest, depending on the broken systemID, you could get a
      // FileNotFoundException which is also an IOException, which will
      // also be processed by this handler....

      final int len = systemId.length();
      int i = 0;
      while (i < len && Verifier.isXMLWhitespace(systemId.charAt(i))) {
        i++;
      }
      if (i < len && '<' == systemId.charAt(i)) {
        // our systemID URI has a '<' - this is likely the problem.
        MalformedURLException mx = new MalformedURLException(
          "SAXBuilder.build(String) expects the String to be " +
          "a systemID, but in this instance it appears to be " +
          "actual XML data.");
        // include the original problem for accountability, and perhaps
        // a false positive.... though very unlikely
        mx.initCause(ioe);
        throw mx;
      }
      // it is likely not an XML document - re-throw the exception
      throw ioe;
    }
  }
}
