// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.codehaus.stax2.XMLStreamReader2;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.Verifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;

import static javax.xml.stream.XMLStreamConstants.*;

// DTD, COMMENT and PROCESSING_INSTRUCTION are ignored
@ApiStatus.Internal
public final class SafeStAXStreamBuilder {
  public static final SafeJdomFactory FACTORY = new SafeJdomFactory.BaseSafeJdomFactory();

  static Document buildDocument(@NotNull XMLStreamReader2 stream) throws XMLStreamException {
    int state = stream.getEventType();

    if (START_DOCUMENT != state) {
      throw new XMLStreamException("JDOM requires that XMLStreamReaders are at their beginning when being processed.");
    }

    Document document = new Document();

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
          document.setRootElement(processElementFragment(stream, true, true, FACTORY));
          break;

        case CHARACTERS:
          String badTxt = stream.getText();
          if (!Verifier.isAllXMLWhitespace(badTxt)) {
            throw new XMLStreamException("Unexpected XMLStream event at Document level: CHARACTERS (" + badTxt + ")");
          }
          // otherwise, ignore the chars
          break;

        default:
          throw new XMLStreamException("Unexpected XMLStream event at Document level:" + state);
      }

      if (stream.hasNext()) {
        state = stream.next();
      }
      else {
        throw new XMLStreamException("Unexpected end-of-XMLStreamReader");
      }
    }
    return document;
  }

  public static Element build(@NotNull XMLStreamReader2 stream,
                              @SuppressWarnings("SameParameterValue") boolean isIgnoreBoundaryWhitespace,
                              boolean isNsSupported,
                              @NotNull SafeJdomFactory factory) throws XMLStreamException {
    int state = stream.getEventType();

    if (state != START_DOCUMENT) {
      throw new XMLStreamException("JDOM requires that XMLStreamReaders are at their beginning when being processed");
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
          rootElement = processElementFragment(stream, isIgnoreBoundaryWhitespace, isNsSupported, factory);
          break;

        default:
          throw new XMLStreamException("Unexpected XMLStream event " + state);
      }

      if (stream.hasNext()) {
        state = stream.next();
      }
      else {
        throw new XMLStreamException("Unexpected end-of-XMLStreamReader");
      }
    }

    if (rootElement == null) {
      // to avoid NPE
      return new Element("empty");
    }
    return rootElement;
  }

  public static @NotNull Element processElementFragment(@NotNull XMLStreamReader2 reader,
                                                        boolean isIgnoreBoundaryWhitespace,
                                                        boolean isNsSupported,
                                                        @NotNull SafeJdomFactory factory) throws XMLStreamException {
    Element fragment = processElement(reader, isNsSupported, factory);
    Element current = fragment;
    int depth = 1;
    while (depth > 0 && reader.hasNext()) {
      switch (reader.next()) {
        case START_ELEMENT:
          Element tmp = processElement(reader, isNsSupported, factory);
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
          break;

        case CHARACTERS:
          if (!isIgnoreBoundaryWhitespace || !reader.isWhiteSpace()) {
            current.addContent(factory.text(reader.getText()));
          }
          break;

        case ENTITY_REFERENCE:
        case COMMENT:
        case PROCESSING_INSTRUCTION:
          break;

        default:
          throw new XMLStreamException("Unexpected XMLStream event " + reader.getEventType(), reader.getLocation());
      }
    }

    return fragment;
  }

  private static @NotNull Element processElement(@NotNull XMLStreamReader2 reader,
                                                 boolean isNsSupported,
                                                 @NotNull SafeJdomFactory factory) {
    Element element = factory.element(reader.getLocalName(), isNsSupported
                                                             ? Namespace.getNamespace(reader.getPrefix(), reader.getNamespaceURI())
                                                             : Namespace.NO_NAMESPACE);
    // handle attributes
    for (int i = 0, len = reader.getAttributeCount(); i < len; i++) {
      element.setAttribute(factory.attribute(
        reader.getAttributeLocalName(i),
        reader.getAttributeValue(i),
        isNsSupported ? Namespace.getNamespace(reader.getAttributePrefix(i), reader.getAttributeNamespace(i)) : Namespace.NO_NAMESPACE
      ));
    }

    if (isNsSupported) {
      // handle namespaces
      for (int i = 0, len = reader.getNamespaceCount(); i < len; i++) {
        element.addNamespaceDeclaration(Namespace.getNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i)));
      }
    }

    return element;
  }
}
