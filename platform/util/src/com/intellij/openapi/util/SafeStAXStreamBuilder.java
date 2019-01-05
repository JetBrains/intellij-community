// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jdom.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.*;

// DTD, COMMENT and PROCESSING_INSTRUCTION wi
final class SafeStAXStreamBuilder {
  static final SafeJdomFactory FACTORY = new SafeJdomFactory.BaseSafeJdomFactory();

  static Document buildDocument(@NotNull XMLStreamReader stream, @SuppressWarnings("SameParameterValue") boolean isIgnoreBoundaryWhitespace) throws JDOMException, XMLStreamException {
    int state = stream.getEventType();

    if (START_DOCUMENT != state) {
      throw new JDOMException("JDOM requires that XMLStreamReaders are at their beginning when being processed.");
    }

    final Document document = new Document();

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
        case COMMENT:
        case PROCESSING_INSTRUCTION:
        case SPACE:
          break;

        case START_ELEMENT:
          document.setRootElement(processElementFragment(stream, isIgnoreBoundaryWhitespace, FACTORY));
          break;

        case CHARACTERS:
          final String badTxt = stream.getText();
          if (!Verifier.isAllXMLWhitespace(badTxt)) {
            throw new JDOMException("Unexpected XMLStream event at Document level: CHARACTERS (" + badTxt + ")");
          }
          // otherwise ignore the chars.
          break;

        default:
          throw new JDOMException("Unexpected XMLStream event at Document level:" + state);
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

  static Element build(@NotNull XMLStreamReader stream, @SuppressWarnings("SameParameterValue") boolean isIgnoreBoundaryWhitespace, @NotNull SafeJdomFactory factory) throws JDOMException, XMLStreamException {
    int state = stream.getEventType();

    if (START_DOCUMENT != state) {
      throw new JDOMException("JDOM requires that XMLStreamReaders are at their beginning when being processed");
    }

    Element rootElement = null;
    while (state != END_DOCUMENT) {
      switch (state) {
        case START_DOCUMENT:
        case SPACE:
        case CHARACTERS:
        case COMMENT:
        case PROCESSING_INSTRUCTION:
        case DTD:
          break;

        case START_ELEMENT:
          rootElement = processElementFragment(stream, isIgnoreBoundaryWhitespace, factory);
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

  private static Element processElementFragment(@NotNull XMLStreamReader reader, boolean isIgnoreBoundaryWhitespace, @NotNull SafeJdomFactory factory) throws XMLStreamException, JDOMException {
    if (reader.getEventType() != START_ELEMENT) {
      throw new JDOMException("JDOM requires that the XMLStreamReader is at the START_ELEMENT state when retrieving an Element Fragment.");
    }

    final Element fragment = processElement(reader, factory);
    Element current = fragment;
    int depth = 1;
    while (depth > 0 && reader.hasNext()) {
      switch (reader.next()) {
        case START_ELEMENT:
          Element tmp = processElement(reader, factory);
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
            current.addContent(factory.text(reader.getText(), current));
          }

        case CHARACTERS:
          if (!isIgnoreBoundaryWhitespace || !reader.isWhiteSpace()) {
            current.addContent(factory.text(reader.getText(), current));
          }
          break;

        case ENTITY_REFERENCE:
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
  private static Element processElement(@NotNull XMLStreamReader reader, @NotNull SafeJdomFactory factory) {
    final Element element = factory.element(reader.getLocalName(), Namespace.getNamespace(reader.getPrefix(), reader.getNamespaceURI()));

    // Handle attributes
    for (int i = 0, len = reader.getAttributeCount(); i < len; i++) {
      element.setAttribute(factory.attribute(
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
