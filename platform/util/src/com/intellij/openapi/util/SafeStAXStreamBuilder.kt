// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.codehaus.stax2.XMLStreamReader2
import org.jdom.Document
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Verifier
import org.jetbrains.annotations.ApiStatus
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

// DTD, COMMENT and PROCESSING_INSTRUCTION are ignored
@ApiStatus.Internal
object SafeStAXStreamBuilder {
  val FACTORY: SafeJdomFactory = SafeJdomFactory.BaseSafeJdomFactory()

  @Throws(XMLStreamException::class)
  fun buildDocument(stream: XMLStreamReader2): Document {
    var state = stream.eventType

    if (XMLStreamConstants.START_DOCUMENT != state) {
      throw XMLStreamException("JDOM requires that XMLStreamReaders are at their beginning when being processed.")
    }

    val document = Document()

    while (state != XMLStreamConstants.END_DOCUMENT) {
      when (state) {
        XMLStreamConstants.START_DOCUMENT -> {
          // for the <?xml version="..." standalone=".."?>
          document.baseURI = stream.location.systemId
          document.setProperty("ENCODING_SCHEME", stream.characterEncodingScheme)
          document.setProperty("STANDALONE", stream.isStandalone.toString())
          document.setProperty("ENCODING", stream.encoding)
        }
        XMLStreamConstants.DTD, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION, XMLStreamConstants.SPACE -> {}
        XMLStreamConstants.START_ELEMENT -> document.setRootElement(processElementFragment(stream, true, true, FACTORY))
        XMLStreamConstants.CHARACTERS -> {
          val badTxt = stream.text
          if (!Verifier.isAllXMLWhitespace(badTxt)) {
            throw XMLStreamException("Unexpected XMLStream event at Document level: CHARACTERS ($badTxt)")
          }
        }
        else -> throw XMLStreamException("Unexpected XMLStream event at Document level:$state")
      }
      if (stream.hasNext()) {
        state = stream.next()
      }
      else {
        throw XMLStreamException("Unexpected end-of-XMLStreamReader")
      }
    }
    return document
  }

  @Throws(XMLStreamException::class)
  fun buildNsUnawareAndClose(stream: XMLStreamReader2): Element {
    return build(stream, true, false, FACTORY)
  }

  @Throws(XMLStreamException::class)
  fun build(stream: XMLStreamReader2,
            isIgnoreBoundaryWhitespace: Boolean,
            isNsSupported: Boolean,
            factory: SafeJdomFactory): Element {
    var state = stream.eventType

    if (state != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("JDOM requires that XMLStreamReaders are at their beginning when being processed")
    }

    var rootElement: Element? = null
    while (state != XMLStreamConstants.END_DOCUMENT) {
      when (state) {
        XMLStreamConstants.START_DOCUMENT, XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION, XMLStreamConstants.DTD -> {}
        XMLStreamConstants.START_ELEMENT -> rootElement = processElementFragment(stream, isIgnoreBoundaryWhitespace, isNsSupported, factory)
        else -> throw XMLStreamException("Unexpected XMLStream event $state")
      }
      if (stream.hasNext()) {
        state = stream.next()
      }
      else {
        throw XMLStreamException("Unexpected end-of-XMLStreamReader")
      }
    }

    if (rootElement == null) {
      // to avoid NPE
      return Element("empty")
    }
    return rootElement
  }

  @Throws(XMLStreamException::class)
  fun processElementFragment(reader: XMLStreamReader2,
                             isIgnoreBoundaryWhitespace: Boolean,
                             isNsSupported: Boolean,
                             factory: SafeJdomFactory): Element {
    val fragment = processElement(reader, isNsSupported, factory)
    var current = fragment
    var depth = 1
    while (depth > 0 && reader.hasNext()) {
      when (reader.next()) {
        XMLStreamConstants.START_ELEMENT -> {
          val tmp = processElement(reader, isNsSupported, factory)
          current.addContent(tmp)
          current = tmp
          depth++
        }
        XMLStreamConstants.END_ELEMENT -> {
          current = current.parentElement
          depth--
        }
        XMLStreamConstants.CDATA -> current.addContent(factory.cdata(reader.text))
        XMLStreamConstants.SPACE -> if (!isIgnoreBoundaryWhitespace) {
          current.addContent(factory.text(reader.text))
        }
        XMLStreamConstants.CHARACTERS -> if (!isIgnoreBoundaryWhitespace || !reader.isWhiteSpace) {
          current.addContent(factory.text(reader.text))
        }
        XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {}
        else -> throw XMLStreamException("Unexpected XMLStream event " + reader.eventType, reader.location)
      }
    }

    return fragment
  }

  private fun processElement(reader: XMLStreamReader2,
                             isNsSupported: Boolean,
                             factory: SafeJdomFactory): Element {
    val element = factory.element(reader.localName, if (isNsSupported
    ) Namespace.getNamespace(reader.prefix, reader.namespaceURI)
    else Namespace.NO_NAMESPACE)
    // handle attributes
    val attributeCount = reader.attributeCount
    if (attributeCount != 0) {
      val list = element.initAttributeList(attributeCount)
      for (i in 0 until attributeCount) {
        list.doAdd(factory.attribute(
          reader.getAttributeLocalName(i),
          reader.getAttributeValue(i),
          if (isNsSupported) Namespace.getNamespace(reader.getAttributePrefix(i), reader.getAttributeNamespace(i))
          else Namespace.NO_NAMESPACE
        ))
      }
    }

    if (isNsSupported) {
      // handle namespaces
      var i = 0
      val len = reader.namespaceCount
      while (i < len) {
        element.addNamespaceDeclaration(Namespace.getNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i)))
        i++
      }
    }

    return element
  }
}
