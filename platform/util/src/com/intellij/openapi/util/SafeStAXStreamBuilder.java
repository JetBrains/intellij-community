// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jdom.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.*;

// DTD, COMMENT and PROCESSING_INSTRUCTION wi
final class SafeStAXStreamBuilder {
  private static final JDOMFactory factory = new DefaultJDOMFactory();

  static Document buildDocument(@NotNull XMLStreamReader stream, boolean isIgnoreBoundaryWhitespace) throws JDOMException, XMLStreamException {
    int state = stream.getEventType();

    if (START_DOCUMENT != state) {
      throw new JDOMException("JDOM requires that XMLStreamReaders are at their beginning when being processed.");
    }

    final Document document = factory.document(null);

    while (state != END_DOCUMENT) {
      switch (state) {
        case START_DOCUMENT:
          // for the <?xml version="..." standalone=".."?>
          document.setBaseURI(stream.getLocation().getSystemId());
          document.setProperty("ENCODING_SCHEME", stream.getCharacterEncodingScheme());
          document.setProperty("STANDALONE", String.valueOf(stream.isStandalone()));
          document.setProperty("ENCODING", stream.getEncoding());
          break;

        case DTD:
          break;

        case START_ELEMENT:
          document.setRootElement(processElementFragment(stream, isIgnoreBoundaryWhitespace));
          break;

        case END_ELEMENT:
          throw new JDOMException("Unexpected XMLStream event at Document level: END_ELEMENT");
        case ENTITY_REFERENCE:
          throw new JDOMException("Unexpected XMLStream event at Document level: ENTITY_REFERENCE");
        case CDATA:
          throw new JDOMException("Unexpected XMLStream event at Document level: CDATA");
        case SPACE:
          // Can happen when XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE is set to true
          document.addContent(factory.text(stream.getText()));
          break;
        case CHARACTERS:
          final String badTxt = stream.getText();
          if (!Verifier.isAllXMLWhitespace(badTxt)) {
            throw new JDOMException("Unexpected XMLStream event at Document level: CHARACTERS (" + badTxt + ")");
          }
          // otherwise ignore the chars.
          break;

        case COMMENT:
        case PROCESSING_INSTRUCTION:
          break;

        default:
          throw new JDOMException("Unexpected XMLStream event " + state);
      }

      if (stream.hasNext()) {
        state = stream.next();
      }
      else {
        throw new JDOMException("Unexpected end-of-XMLStreamReader");
      }
    }
    return document;
  }

  static Element build(@NotNull XMLStreamReader stream, boolean isIgnoreBoundaryWhitespace) throws JDOMException, XMLStreamException {
    int state = stream.getEventType();

    if (START_DOCUMENT != state) {
      throw new JDOMException("JDOM requires that XMLStreamReaders are at their beginning when being processed");
    }

    Element rootElement = null;
    while (state != END_DOCUMENT) {
      switch (state) {
        case START_DOCUMENT:
          break;

        case DTD:
          break;

        case START_ELEMENT:
          rootElement = processElementFragment(stream, isIgnoreBoundaryWhitespace);
          break;

        case END_ELEMENT:
          throw new JDOMException("Unexpected XMLStream event at Document level: END_ELEMENT");
        case ENTITY_REFERENCE:
          throw new JDOMException("Unexpected XMLStream event at Document level: ENTITY_REFERENCE");
        case CDATA:
          throw new JDOMException("Unexpected XMLStream event at Document level: CDATA");
        case SPACE:
        case CHARACTERS:
        case COMMENT:
        case PROCESSING_INSTRUCTION:
          break;

        default:
          throw new JDOMException("Unexpected XMLStream event " + state);
      }

      if (stream.hasNext()) {
        state = stream.next();
      }
      else {
        throw new JDOMException("Unexpected end-of-XMLStreamReader");
      }
    }

    if (rootElement == null) {
      // to avoid NPE
      return new Element("empty");
    }
    return rootElement;
  }

  private static Element processElementFragment(@NotNull XMLStreamReader reader, boolean isIgnoreBoundaryWhitespace) throws XMLStreamException, JDOMException {
    if (reader.getEventType() != START_ELEMENT) {
      throw new JDOMException("JDOM requires that the XMLStreamReader is at the START_ELEMENT state when retrieving an Element Fragment.");
    }

    final Element fragment = processElement(reader);
    Element current = fragment;
    int depth = 1;
    while (depth > 0 && reader.hasNext()) {
      switch (reader.next()) {
        case START_ELEMENT:
          Element tmp = processElement(reader);
          current.addContent(tmp);
          current = tmp;
          depth++;
          break;
        case END_ELEMENT:
          current = current.getParentElement();
          depth--;
          break;
        case CDATA:
          current.addContent(factory.cdata(reader.getText()));
          break;

        case SPACE:
          if (!isIgnoreBoundaryWhitespace) {
            current.addContent(factory.text(reader.getText()));
          }

        case CHARACTERS:
          if (!isIgnoreBoundaryWhitespace || !reader.isWhiteSpace()) {
            current.addContent(factory.text(reader.getText()));
          }
          break;

        case ENTITY_REFERENCE:
          current.addContent(factory.entityRef(reader.getLocalName()));
          break;

        case COMMENT:
        case PROCESSING_INSTRUCTION:
          break;

        default:
          throw new JDOMException("Unexpected XMLStream event " + reader.getEventType());
      }
    }

    return fragment;
  }

  @NotNull
  private static Element processElement(@NotNull XMLStreamReader reader) {
    final Element element = factory.element(reader.getLocalName(), Namespace.getNamespace(reader.getPrefix(), reader.getNamespaceURI()));

    // Handle attributes
    for (int i = 0, len = reader.getAttributeCount(); i < len; i++) {
      factory.setAttribute(element, factory.attribute(
        reader.getAttributeLocalName(i),
        reader.getAttributeValue(i),
        AttributeType.getAttributeType(reader.getAttributeType(i)),
        Namespace.getNamespace(reader.getAttributePrefix(i), reader.getAttributeNamespace(i))));
    }

    // Handle Namespaces
    for (int i = 0, len = reader.getNamespaceCount(); i < len; i++) {
      element.addNamespaceDeclaration(Namespace.getNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i)));
    }

    return element;
  }
}
