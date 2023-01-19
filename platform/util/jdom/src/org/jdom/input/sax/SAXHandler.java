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

package org.jdom.input.sax;

import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.xml.sax.Attributes;
import org.xml.sax.DTDHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A support class for {@link SAXBuilder} which listens for SAX events.
 * <p>
 * People overriding this class are cautioned to ensure that the implementation
 * of the cleanUp() method resets to a virgin state. The cleanUp() method will
 * be called when this SAXHandler is reset(), which may happen multiple times
 * between parses. The cleanUp() method must ensure that there are no references
 * remaining to any external instances.
 * <p>
 * Overriding of this class is permitted to allow for different handling of SAX
 * events. Once you have created a subclass of this, you also need to create a
 * custom implementation of {@link SAXHandlerFactory} to supply your instances
 * to {@link SAXBuilder}
 * <p>
 * If the XMLReader producing the SAX Events supports a document Locator, then
 * this instance will use the locator to supply the line and column data from
 * the SAX locator to the JDOMFactory. <strong>Note:</strong> the SAX
 * specification for the SAX Locator indicates that the line and column
 * represent the position of the <strong>end</strong> of the SAX Event. For
 * example, the line and column of the simple XML <code>&lt;root /&gt;</code>
 * would be line 1, column 9.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Philip Nelson
 * @author Bradley S. Huffman
 * @author phil@triloggroup.com
 * @author Rolf Lear
 * @see org.jdom.input.sax
 */
public class SAXHandler extends DefaultHandler implements LexicalHandler, DeclHandler, DTDHandler {

  /**
   * The JDOMFactory used for JDOM object creation
   */
  private final JDOMFactory factory;

  /**
   * Temporary holder for namespaces that have been declared with
   * startPrefixMapping, but are not yet available on the element
   */
  private final List<Namespace> declaredNamespaces = new ArrayList<>(
    32);

  /**
   * Temporary holder for the internal subset
   */
  private final StringBuilder internalSubset = new StringBuilder();

  /**
   * Temporary holder for Text and CDATA
   */
  private final TextBuffer textBuffer = new TextBuffer();

  /**
   * The external entities defined in this document
   */
  private final Map<String, String[]> externalEntities = new HashMap<>();

  /**
   * <code>Document</code> object being built - must be cleared on reset()
   */
  private Document currentDocument = null;

  /**
   * <code>Element</code> object being built - must be cleared on reset()
   */
  private Element currentElement = null;

  /**
   * The SAX Locator object provided by the parser
   */
  private Locator currentLocator = null;

  /**
   * Indicator of where in the document we are - must be reset()
   */
  private boolean atRoot = true;

  /**
   * Indicator of whether we are in the DocType. Note that the DTD consists of
   * both the internal subset (inside the <!DOCTYPE> tag) and the external
   * subset (in a separate .dtd file). - must be reset()
   */
  private boolean inDTD = false;

  /**
   * Indicator of whether we are in the internal subset - must be reset()
   */
  private boolean inInternalSubset = false;

  /**
   * Indicator of whether we previously were in a CDATA - must be reset()
   */
  private boolean previousCDATA = false;

  /**
   * Indicator of whether we are in a CDATA - must be reset()
   */
  private boolean inCDATA = false;

  /**
   * Indicator of whether we should expand entities
   */
  private boolean expand = true;

  /**
   * Indicator of whether we are actively suppressing (non-expanding) a
   * current entity - must be reset()
   */
  private boolean suppress = false;

  /**
   * How many nested entities we're currently within - must be reset()
   */
  private int entityDepth = 0; // XXX may not be necessary anymore?

  /**
   * Whether to ignore ignorable whitespace
   */
  private boolean ignoringWhite = false;

  /**
   * Whether to ignore text containing all whitespace
   */
  private boolean ignoringBoundaryWhite = false;

  private int lastline = 0, lastcol = 0;

  /**
   * This will create a new <code>SAXHandler</code> that listens to SAX events
   * and creates a JDOM Document. The objects will be constructed using the
   * provided factory.
   *
   * @param factory <code>JDOMFactory</code> to be used for constructing objects
   */
  public SAXHandler(final JDOMFactory factory) {
    this.factory = factory != null ? factory : new DefaultJDOMFactory();
    reset();
  }

  /**
   * Override this method if you are a subclasser, and you want to clear the
   * state of your SAXHandler instance in preparation for a new parse.
   */
  private void resetSubCLass() {
    // override this if you subclass SAXHandler.
    // it will be called after the base core SAXHandler is reset.
  }

  protected void pushElement(final Element element) {
    if (atRoot) {
      currentDocument.setRootElement(element); // XXX should we use a
      // factory call?
      atRoot = false;
    }
    else {
      factory.addContent(currentElement, element);
    }
    currentElement = element;
  }

  /**
   * Restore this SAXHandler to a clean state ready for another parse round.
   * All internal variables are cleared to an initialized state, and then the
   * resetSubClass() method is called to clear any methods that a subclass may
   * need to have reset.
   */
  public final void reset() {
    currentLocator = null;
    currentDocument = factory.document(null);
    currentElement = null;
    atRoot = true;
    inDTD = false;
    inInternalSubset = false;
    previousCDATA = false;
    inCDATA = false;
    suppress = false;
    entityDepth = 0;
    declaredNamespaces.clear();
    internalSubset.setLength(0);
    textBuffer.clear();
    externalEntities.clear();
    resetSubCLass();
  }

  /**
   * Returns the document. Should be called after parsing is complete.
   *
   * @return <code>Document</code> - Document that was built
   */
  public Document getDocument() {
    return currentDocument;
  }

  /**
   * Returns the factory used for constructing objects.
   *
   * @return <code>JDOMFactory</code> - the factory used for constructing
   * objects.
   * @see #SAXHandler(JDOMFactory)
   */
  public JDOMFactory getFactory() {
    return factory;
  }

  /**
   * This sets whether or not to expand entities during the build. A true
   * means to expand entities as normal content. A false means to leave
   * entities unexpanded as <code>EntityRef</code> objects. The default is
   * true.
   *
   * @param expand <code>boolean</code> indicating whether entity expansion should
   *               occur.
   */
  public void setExpandEntities(final boolean expand) {
    this.expand = expand;
  }

  /**
   * Returns whether or not entities will be expanded during the build.
   *
   * @return <code>boolean</code> - whether entity expansion will occur during
   * build.
   * @see #setExpandEntities
   */
  boolean getExpandEntities() {
    return expand;
  }

  /**
   * Specifies whether or not the parser should elminate whitespace in element
   * content (sometimes known as "ignorable whitespace") when building the
   * document. Only whitespace which is contained within element content that
   * has an element only content model will be eliminated (see XML Rec 3.2.1).
   * For this setting to take effect requires that validation be turned on.
   * The default value of this setting is <code>false</code>.
   *
   * @param ignoringWhite Whether to ignore ignorable whitespace
   */
  public void setIgnoringElementContentWhitespace(final boolean ignoringWhite) {
    this.ignoringWhite = ignoringWhite;
  }

  /**
   * Specifies whether the parser should eliminate text() nodes
   * containing only whitespace when building the document. See
   * {@link SAXBuilder#setIgnoringBoundaryWhitespace(boolean)}.
   *
   * @param ignoringBoundaryWhite Whether to ignore only whitespace content
   */
  public void setIgnoringBoundaryWhitespace(
    final boolean ignoringBoundaryWhite) {
    this.ignoringBoundaryWhite = ignoringBoundaryWhite;
  }

  /**
   * Returns whether or not the parser will elminate element content
   * containing only whitespace.
   *
   * @return <code>boolean</code> - whether only whitespace content will be
   * ignored during build.
   * @see #setIgnoringBoundaryWhitespace
   */
  boolean getIgnoringBoundaryWhitespace() {
    return ignoringBoundaryWhite;
  }

  /**
   * Returns whether or not the parser will elminate whitespace in element
   * content (sometimes known as "ignorable whitespace") when building the
   * document.
   *
   * @return <code>boolean</code> - whether ignorable whitespace will be
   * ignored during build.
   * @see #setIgnoringElementContentWhitespace
   */
  boolean getIgnoringElementContentWhitespace() {
    return ignoringWhite;
  }

  @Override
  public void startDocument() {
    if (currentLocator != null) {
      currentDocument.setBaseURI(currentLocator.getSystemId());
    }
  }

  /**
   * This is called when the parser encounters an external entity declaration.
   *
   * @param name     entity name
   * @param publicID public id
   * @param systemID system id
   */
  @Override
  public void externalEntityDecl(final String name, final String publicID,
                                 final String systemID) {
    // Store the public and system ids for the name
    externalEntities.put(name, new String[]{publicID, systemID});

    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!ENTITY ").append(name);
    appendExternalId(publicID, systemID);
    internalSubset.append(">\n");
  }

  /**
   * This handles an attribute declaration in the internal subset.
   *
   * @param eName        <code>String</code> element name of attribute
   * @param aName        <code>String</code> attribute name
   * @param type         <code>String</code> attribute type
   * @param valueDefault <code>String</code> default value of attribute
   * @param value        <code>String</code> value of attribute
   */
  @Override
  public void attributeDecl(final String eName, final String aName,
                            final String type, final String valueDefault, final String value) {

    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!ATTLIST ").append(eName).append(' ')
      .append(aName).append(' ').append(type).append(' ');
    if (valueDefault != null) {
      internalSubset.append(valueDefault);
    }
    else {
      internalSubset.append('\"').append(value).append('\"');
    }
    if ((valueDefault != null) && (valueDefault.equals("#FIXED"))) {
      internalSubset.append(" \"").append(value).append('\"');
    }
    internalSubset.append(">\n");
  }

  /**
   * Handle an element declaration in a DTD.
   *
   * @param name  <code>String</code> name of element
   * @param model <code>String</code> model of the element in DTD syntax
   */
  @Override
  public void elementDecl(final String name, final String model) {
    // Skip elements that come from the external subset
    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!ELEMENT ").append(name).append(' ')
      .append(model).append(">\n");
  }

  /**
   * Handle an internal entity declaration in a DTD.
   *
   * @param name  <code>String</code> name of entity
   * @param value <code>String</code> value of the entity
   */
  @Override
  public void internalEntityDecl(final String name, final String value) {

    // Skip entities that come from the external subset
    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!ENTITY ");
    if (name.startsWith("%")) {
      internalSubset.append("% ").append(name.substring(1));
    }
    else {
      internalSubset.append(name);
    }
    internalSubset.append(" \"").append(value).append("\">\n");
  }

  /**
   * This will indicate that a processing instruction has been encountered.
   * (The XML declaration is not a processing instruction and will not be
   * reported.)
   *
   * @param target <code>String</code> target of PI
   * @param data   <code>String</code> containing all data sent to the PI. This
   *               typically looks like one or more attribute value pairs.
   * @throws SAXException when things go wrong
   */
  @Override
  public void processingInstruction(final String target, final String data)
    throws SAXException {

    if (suppress) {
      return;
    }

    flushCharacters();

    final ProcessingInstruction pi = (currentLocator == null) ? factory
      .processingInstruction(target, data) : factory
                                       .processingInstruction(currentLocator.getLineNumber(),
                                                              currentLocator.getColumnNumber(), target, data);

    if (atRoot) {
      factory.addContent(currentDocument, pi);
    }
    else {
      factory.addContent(getCurrentElement(), pi);
    }
  }

  /**
   * This indicates that an unresolvable entity reference has been
   * encountered, normally because the external DTD subset has not been read.
   *
   * @param name <code>String</code> name of entity
   * @throws SAXException when things go wrong
   */
  @Override
  public void skippedEntity(final String name) throws SAXException {

    // We don't handle parameter entity references.
    if (name.startsWith("%")) {
      return;
    }

    flushCharacters();

    final EntityRef er = currentLocator == null ? factory.entityRef(name)
                                                : factory.entityRef(currentLocator.getLineNumber(),
                                                                    currentLocator.getColumnNumber(), name);

    factory.addContent(getCurrentElement(), er);
  }

  /**
   * This will add the prefix mapping to the JDOM <code>Document</code>
   * object.
   *
   * @param prefix <code>String</code> namespace prefix.
   * @param uri    <code>String</code> namespace URI.
   */
  @Override
  public void startPrefixMapping(final String prefix, final String uri) {
    if (suppress) {
      return;
    }

    final Namespace ns = Namespace.getNamespace(prefix, uri);
    declaredNamespaces.add(ns);
  }

  /**
   * This reports the occurrence of an actual element. It will include the
   * element's attributes, except XML vocabulary specific
   * attributes, such as <code>xmlns:[namespace prefix]</code> and
   * <code>xsi:schemaLocation</code>.
   *
   * @param namespaceURI <code>String</code> namespace URI this element is associated with,
   *                     or an empty <code>String</code>
   * @param localName    <code>String</code> name of element (with no namespace prefix, if
   *                     one is present)
   * @param qName        <code>String</code> XML 1.0 version of element name: [namespace
   *                     prefix]:[localName]
   * @param atts         <code>Attributes</code> list for this element
   * @throws SAXException when things go wrong
   */
  @Override
  public void startElement(final String namespaceURI, String localName,
                           final String qName, final Attributes atts) throws SAXException {
    if (suppress) {
      return;
    }

    String prefix = "";

    // If QName is set, then set prefix and local name as necessary
    if (!"".equals(qName)) {
      final int colon = qName.indexOf(':');

      if (colon > 0) {
        prefix = qName.substring(0, colon);
      }

      // If local name is not set, try to get it from the QName
      if ((localName == null) || (localName.isEmpty())) {
        localName = qName.substring(colon + 1);
      }
    }
    // At this point either prefix and localName are set correctly or
    // there is an error in the parser.

    final Namespace namespace = Namespace
      .getNamespace(prefix, namespaceURI);
    final Element element = currentLocator == null ? factory.element(
      localName, namespace) : factory.element(
      currentLocator.getLineNumber(),
      currentLocator.getColumnNumber(), localName, namespace);

    // Take leftover declared namespaces and add them to this element's
    // map of namespaces
    if (declaredNamespaces.size() > 0) {
      transferNamespaces(element);
    }

    flushCharacters();

    if (atRoot) {
      factory.setRoot(currentDocument, element); // Yes, use a factory
      // call...
      atRoot = false;
    }
    else {
      factory.addContent(getCurrentElement(), element);
    }
    currentElement = element;

    // Handle attributes
    for (int i = 0, len = atts.getLength(); i < len; i++) {

      String attPrefix = "";
      String attLocalName = atts.getLocalName(i);
      final String attQName = atts.getQName(i);
      final boolean specified = !(atts instanceof Attributes2) || ((Attributes2)atts).isSpecified(i);

      // If attribute QName is set, then set attribute prefix and
      // attribute local name as necessary
      if (!attQName.isEmpty()) {
        // Bypass any xmlns attributes which might appear, as we got
        // them already in startPrefixMapping(). This is sometimes
        // necessary when SAXHandler is used with another source than
        // SAXBuilder, as with JDOMResult.
        if (attQName.startsWith("xmlns:") || attQName.equals("xmlns")) {
          continue;
        }

        final int attColon = attQName.indexOf(':');

        if (attColon > 0) {
          attPrefix = attQName.substring(0, attColon);
        }

        // If localName is not set, try to get it from the QName
        if ("".equals(attLocalName)) {
          attLocalName = attQName.substring(attColon + 1);
        }
      }

      final AttributeType attType = AttributeType.getAttributeType(atts
                                                                     .getType(i));
      final String attValue = atts.getValue(i);
      final String attURI = atts.getURI(i);

      if (XMLConstants.XMLNS_ATTRIBUTE.equals(attLocalName)
          || XMLConstants.XMLNS_ATTRIBUTE.equals(attPrefix)
          || XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attURI)) {
        // use the actual Namespace to check too, because, in theory, a
        // namespace-aware parser does not need to set the qName unless
        // the namespace-prefixes feature is set as well.
        continue;
      }
      // At this point either attPrefix and attLocalName are set
      // correctly or there is an error in the parser.

      // just one thing to sort out....
      // the prefix for the namespace.
      if (!"".equals(attURI) && attPrefix.isEmpty()) {
        // the localname and qName are the same, but there is a
        // Namspace URI. We need to figure out the namespace prefix.
        // this is an unusual condition. Currently the only known
        // trigger
        // is when there is a fixed/defaulted attribute from a
        // validating
        // XMLSchema, and the attribute is in a different namespace
        // than the rest of the document, this happens whenever there
        // is an attribute definition that has form="qualified".
        // <xs:attribute name="attname" form="qualified" ... />
        // or the schema sets attributeFormDefault="qualified"
        final HashMap<String, Namespace> tmpmap = new HashMap<>();
        for (final Namespace nss : element.getNamespacesInScope()) {
          if (nss.getPrefix().length() > 0
              && nss.getURI().equals(attURI)) {
            attPrefix = nss.getPrefix();
            break;
          }
          tmpmap.put(nss.getPrefix(), nss);
        }

        if (attPrefix.isEmpty()) {
          // we cannot find a 'prevailing' namespace that has a prefix
          // that is for this namespace.
          // This basically means that there's an XMLSchema, for the
          // DEFAULT namespace, and there's a defaulted/fixed
          // attribute definition in the XMLSchema that's targeted
          // for this namespace,... but, the user has either not
          // declared a prefixed version of the namespace, or has
          // re-declared the same prefix at a lower level with a
          // different namespace.
          // All of these things are possible.
          // Create some sort of default prefix.
          int cnt = 0;
          final String base = "attns";
          String pfx = base + cnt;
          while (tmpmap.containsKey(pfx)) {
            cnt++;
            pfx = base + cnt;
          }
          attPrefix = pfx;
        }
      }
      final Namespace attNs = Namespace.getNamespace(attPrefix, attURI);

      final Attribute attribute = factory.attribute(attLocalName, attValue, attType, attNs);
      if (!specified) {
        // it is a DTD defaulted value.
        attribute.setSpecified(false);
      }
      factory.setAttribute(element, attribute);
    }
  }

  /**
   * This will take the supplied <code>{@link Element}</code> and transfer its
   * namespaces to the global namespace storage.
   *
   * @param element <code>Element</code> to read namespaces from.
   */
  private void transferNamespaces(final Element element) {
    for (final Namespace ns : declaredNamespaces) {
      if (ns != element.getNamespace()) {
        element.addNamespaceDeclaration(ns);
      }
    }
    declaredNamespaces.clear();
  }

  /**
   * This will report character data (within an element).
   *
   * @param ch     <code>char[]</code> character array with character data
   * @param start  <code>int</code> index in array where data starts.
   * @param length <code>int</code> length of data.
   */
  @Override
  public void characters(final char[] ch, final int start, final int length)
    throws SAXException {

    if (suppress || (length == 0 && !inCDATA)) {
      return;
    }

    if (previousCDATA != inCDATA) {
      flushCharacters();
    }

    textBuffer.append(ch, start, length);

    if (currentLocator != null) {
      lastline = currentLocator.getLineNumber();
      lastcol = currentLocator.getColumnNumber();
    }
  }

  /**
   * Capture ignorable whitespace as text. If
   * setIgnoringElementContentWhitespace(true) has been called then this
   * method does nothing.
   *
   * @param ch     <code>[]</code> - char array of ignorable whitespace
   * @param start  <code>int</code> - starting position within array
   * @param length <code>int</code> - length of whitespace after start
   * @throws SAXException when things go wrong
   */
  @Override
  public void ignorableWhitespace(final char[] ch, final int start,
                                  final int length) throws SAXException {
    if (!ignoringWhite) {
      characters(ch, start, length);
    }
  }

  /**
   * This will flush any characters from SAX character calls we've been
   * buffering.
   *
   * @throws SAXException when things go wrong
   */
  protected void flushCharacters() throws SAXException {
    if (ignoringBoundaryWhite) {
      if (!textBuffer.isAllWhitespace()) {
        flushCharacters(textBuffer.toString());
      }
    }
    else {
      flushCharacters(textBuffer.toString());
    }
    textBuffer.clear();
  }

  /**
   * Flush the given string into the document. This is a protected method so
   * subclassers can control text handling without knowledge of the internals
   * of this class.
   *
   * @param data string to flush
   * @throws SAXException if the state of the handler does not allow this.
   */
  private void flushCharacters(final String data) throws SAXException {
    if (data.length() == 0 && !inCDATA) {
      previousCDATA = inCDATA;
      return;
    }

    /*
      This is commented out because of some problems with the inline DTDs
      that Xerces seems to have. if (!inDTD) { if (inEntity) {
      getCurrentElement().setContent(factory.text(data)); } else {
      getCurrentElement().addContent(factory.text(data)); }
     */

    if (previousCDATA) {
      final CDATA cdata = currentLocator == null ? factory.cdata(data)
                                                 : factory.cdata(lastline, lastcol, data);
      factory.addContent(getCurrentElement(), cdata);
    }
    else if (data.length() != 0) {
      final Text text = currentLocator == null ? factory.text(data)
                                               : factory.text(lastline, lastcol, data);
      factory.addContent(getCurrentElement(), text);
    }

    previousCDATA = inCDATA;
  }

  /**
   * Indicates the end of an element (<code>&lt;/[element name]&gt;</code>) is
   * reached. Note that the parser does not distinguish between empty elements
   * and non-empty elements, so this will occur uniformly.
   *
   * @param namespaceURI <code>String</code> URI of namespace this element is associated
   *                     with
   * @param localName    <code>String</code> name of element without prefix
   * @param qName        <code>String</code> name of element in XML 1.0 form
   * @throws SAXException when things go wrong
   */
  @Override
  public void endElement(final String namespaceURI, final String localName,
                         final String qName) throws SAXException {

    if (suppress) {
      return;
    }

    flushCharacters();

    if (!atRoot) {
      final Parent p = currentElement.getParent();
      if (p instanceof Document) {
        atRoot = true;
      }
      else {
        currentElement = (Element)p;
      }
    }
    else {
      throw new SAXException(
        "Ill-formed XML document (missing opening tag for "
        + localName + ")");
    }
  }

  /**
   * This will signify that a DTD is being parsed, and can be used to ensure
   * that comments and other lexical structures in the DTD are not added to
   * the JDOM <code>Document</code> object.
   *
   * @param name     <code>String</code> name of element listed in DTD
   * @param publicID <code>String</code> public ID of DTD
   * @param systemID <code>String</code> system ID of DTD
   */
  @Override
  public void startDTD(final String name, final String publicID,
                       final String systemID) throws SAXException {

    flushCharacters(); // Is this needed here?

    final DocType doctype = currentLocator == null ? factory.docType(name,
                                                                     publicID, systemID) : factory.docType(
      currentLocator.getLineNumber(),
      currentLocator.getColumnNumber(), name, publicID, systemID);
    factory.addContent(currentDocument, doctype);
    inDTD = true;
    inInternalSubset = true;
  }

  /**
   * This signifies that the reading of the DTD is complete.
   */
  @Override
  public void endDTD() {
    currentDocument.getDocType().setInternalSubset(internalSubset.toString());
    inDTD = false;
    inInternalSubset = false;
  }

  @Override
  public void startEntity(final String name) throws SAXException {
    entityDepth++;

    if (expand || entityDepth > 1) {
      // Shortcut out if we're expanding or if we're nested
      return;
    }

    // A "[dtd]" entity indicates the beginning of the external subset
    if (name.equals("[dtd]")) {
      inInternalSubset = false;
      return;
    }

    // Ignore DTD references, and translate the standard 5
    if ((!inDTD) && (!name.equals("amp")) && (!name.equals("lt"))
        && (!name.equals("gt")) && (!name.equals("apos"))
        && (!name.equals("quot"))) {

      String pub = null;
      String sys = null;
      String[] ids = externalEntities.get(name);
      if (ids != null) {
        pub = ids[0]; // may be null, that's OK
        sys = ids[1]; // may be null, that's OK
      }
      /*
        if no current element, this entity belongs to an attribute in
        these cases, it is an error on the part of the parser to call
        startEntity but this will help in some cases. See
        org/xml/sax/
        ext/LexicalHandler.html#startEntity(java.lang.String) for
        more information
       */
      if (!atRoot) {
        flushCharacters();
        final EntityRef entity = currentLocator == null ? factory
          .entityRef(name, pub, sys) : factory.entityRef(
          currentLocator.getLineNumber(),
          currentLocator.getColumnNumber(), name, pub, sys);

        // no way to tell if the entity was from an attribute or
        // element so just assume element
        factory.addContent(getCurrentElement(), entity);
      }
      suppress = true;
    }
  }

  @Override
  public void endEntity(final String name) {
    entityDepth--;
    if (entityDepth == 0) {
      // No way are we suppressing if not in an entity,
      // regardless of the "expand" value
      suppress = false;
    }
    if (name.equals("[dtd]")) {
      inInternalSubset = true;
    }
  }

  /**
   * Report a CDATA section
   */
  @Override
  public void startCDATA() {
    if (suppress) {
      return;
    }

    inCDATA = true;
  }

  /**
   * Report a CDATA section
   */
  @Override
  public void endCDATA() throws SAXException {
    if (suppress) {
      return;
    }

    previousCDATA = true;
    flushCharacters();
    previousCDATA = false;
    inCDATA = false;
  }

  /**
   * This reports that a comments is parsed. If not in the DTD, this comment
   * is added to the current JDOM <code>Element</code>, or the
   * <code>Document</code> itself if at that level.
   *
   * @param ch     <code>ch[]</code> array of comment characters.
   * @param start  <code>int</code> index to start reading from.
   * @param length <code>int</code> length of data.
   * @throws SAXException if the state of the handler disallows this call
   */
  @Override
  public void comment(final char[] ch, final int start, final int length)
    throws SAXException {

    if (suppress) {
      return;
    }

    flushCharacters();

    final String commentText = new String(ch, start, length);
    if (inDTD && inInternalSubset && (!expand)) {
      internalSubset.append("  <!--").append(commentText).append("-->\n");
      return;
    }
    if ((!inDTD) && (!commentText.isEmpty())) {
      final Comment comment = currentLocator == null ? factory
        .comment(commentText) : factory.comment(
        currentLocator.getLineNumber(),
        currentLocator.getColumnNumber(), commentText);
      if (atRoot) {
        factory.addContent(currentDocument, comment);
      }
      else {
        factory.addContent(getCurrentElement(), comment);
      }
    }
  }

  /**
   * Handle the declaration of a Notation in a DTD
   *
   * @param name     name of the notation
   * @param publicID the public ID of the notation
   * @param systemID the system ID of the notation
   */
  @Override
  public void notationDecl(final String name, final String publicID,
                           final String systemID) {

    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!NOTATION ").append(name);
    appendExternalId(publicID, systemID);
    internalSubset.append(">\n");
  }

  /**
   * Handler for unparsed entity declarations in the DTD
   *
   * @param name         <code>String</code> of the unparsed entity decl
   * @param publicID     <code>String</code> of the unparsed entity decl
   * @param systemID     <code>String</code> of the unparsed entity decl
   * @param notationName <code>String</code> of the unparsed entity decl
   */
  @Override
  public void unparsedEntityDecl(final String name, final String publicID,
                                 final String systemID, final String notationName) {

    if (!inInternalSubset) {
      return;
    }

    internalSubset.append("  <!ENTITY ").append(name);
    appendExternalId(publicID, systemID);
    internalSubset.append(" NDATA ").append(notationName);
    internalSubset.append(">\n");
  }

  /**
   * Appends an external ID to the internal subset buffer. Either publicID or
   * systemID may be null, but not both.
   *
   * @param publicID the public ID
   * @param systemID the system ID
   */
  private void appendExternalId(final String publicID, final String systemID) {
    if (publicID != null) {
      internalSubset.append(" PUBLIC \"").append(publicID).append('\"');
    }
    if (systemID != null) {
      if (publicID == null) {
        internalSubset.append(" SYSTEM ");
      }
      else {
        internalSubset.append(' ');
      }
      internalSubset.append('\"').append(systemID).append('\"');
    }
  }

  /**
   * Returns the being-parsed element.
   *
   * @return <code>Element</code> - element being built.
   * @throws SAXException if the state of the handler disallows this call
   */
  public Element getCurrentElement() throws SAXException {
    if (currentElement == null) {
      throw new SAXException(
        "Ill-formed XML document (multiple root elements detected)");
    }
    return currentElement;
  }

  @Override
  public void setDocumentLocator(final Locator locator) {
    this.currentLocator = locator;
  }
}
