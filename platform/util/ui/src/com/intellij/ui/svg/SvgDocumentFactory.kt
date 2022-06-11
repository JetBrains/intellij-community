// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import com.intellij.util.xml.dom.createXmlStreamReader
import org.apache.batik.anim.dom.SVG12DOMImplementation
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.dom.GenericCDATASection
import org.apache.batik.dom.GenericText
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.util.ParsedURL
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

@ApiStatus.Internal
fun createSvgDocument(uri: String?, reader: InputStream) = createSvgDocument(uri, createXmlStreamReader(reader))

@ApiStatus.Internal
fun createSvgDocument(uri: String?, data: ByteArray) = createSvgDocument(uri, createXmlStreamReader(data))

private fun createSvgDocument(uri: String?, xmlStreamReader: XMLStreamReader2): Document {
  val result = try {
    buildDocument(xmlStreamReader)
  }
  catch (e: XMLStreamException) {
    throw TranscoderException(e)
  }
  finally {
    xmlStreamReader.close()
  }

  if (uri != null) {
    result.parsedURL = ParsedURL(uri)
    result.documentURI = uri
  }
  return result
}

private fun buildDocument(reader: XMLStreamReader): SVGOMDocument {
  var state = reader.eventType
  if (XMLStreamConstants.START_DOCUMENT != state) {
    throw TranscoderException("Incorrect state: $state")
  }

  var document: SVGOMDocument? = null

  while (state != XMLStreamConstants.END_DOCUMENT) {
    when (state) {
      XMLStreamConstants.START_DOCUMENT -> {
        assert(document == null)
      }
      XMLStreamConstants.DTD, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION, XMLStreamConstants.SPACE -> {
      }
      XMLStreamConstants.START_ELEMENT -> {
        var version: String? = null
        for (i in 0 until reader.attributeCount) {
          val localName = reader.getAttributeLocalName(i)
          val prefix = reader.getAttributePrefix(i)
          if (prefix.isEmpty() && localName == "version") {
            version = reader.getAttributeValue(i)
            break
          }
        }

        val implementation: SVGDOMImplementation = when {
          version == null || version.isEmpty() || version == "1.0" || version == "1.1" -> SVGDOMImplementation.getDOMImplementation() as SVGDOMImplementation
          version == "1.2" -> SVG12DOMImplementation.getDOMImplementation() as SVGDOMImplementation
          else -> throw TranscoderException("Unsupported SVG version: $version")
        }

        val localName = reader.localName
        document = implementation.createDocument(reader.namespaceURI, getRawName(reader.prefix, localName), null) as SVGOMDocument
        val element = document.documentElement
        readAttributes(element, reader)

        if (localName != "svg") {
          throw TranscoderException("Root element does not match that requested:\nRequested: svg\nFound: $localName")
        }
        processElementFragment(reader, document, implementation, element)
      }
      XMLStreamConstants.CHARACTERS -> {
        val badContent = reader.text
        if (!isAllXMLWhitespace(badContent)) {
          throw TranscoderException("Unexpected XMLStream event at Document level: CHARACTERS ($badContent)")
        }
      }
      else -> throw TranscoderException("Unexpected XMLStream event at Document level:$state")
    }
    state = if (reader.hasNext()) {
      reader.next()
    }
    else {
      throw TranscoderException("Unexpected end-of-XMLStreamReader")
    }
  }
  return document!!
}

private fun processElementFragment(reader: XMLStreamReader, document: SVGOMDocument, factory: SVGDOMImplementation, parent: Element) {
  var depth = 1
  var current: Node = parent
  while (depth > 0 && reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val element = factory.createElementNS(document, reader.namespaceURI, reader.localName)
        readAttributes(element, reader)
        current.appendChild(element)
        current = element
        depth++
      }
      XMLStreamConstants.END_ELEMENT -> {
        current = current.parentNode
        depth--
      }
      XMLStreamConstants.CDATA -> current.appendChild(GenericCDATASection(reader.text, document))
      XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          current.appendChild(GenericText(reader.text, document))
        }
      }
      XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
      }
      else -> throw TranscoderException("Unexpected XMLStream event: ${reader.eventType}")
    }
  }
}

private fun readAttributes(element: Element, reader: XMLStreamReader) {
  for (i in 0 until reader.attributeCount) {
    val localName = reader.getAttributeLocalName(i)
    val prefix = reader.getAttributePrefix(i)
    element.setAttributeNS(reader.getAttributeNamespace(i), getRawName(prefix, localName), reader.getAttributeValue(i))
  }
}

private fun getRawName(prefix: String?, localName: String): String {
  return if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"
}

private fun isAllXMLWhitespace(value: String): Boolean {
  var i = value.length
  while (--i >= 0) {
    val c = value[i]
    if (c != ' ' && c != '\n' && c != '\t' && c != '\r') {
      return false
    }
  }
  return true
}