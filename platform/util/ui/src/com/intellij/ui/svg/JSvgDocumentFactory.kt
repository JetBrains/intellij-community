// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.github.weisj.jsvg.parser.impl.MutableLoaderContext
import com.github.weisj.jsvg.parser.impl.NodeSupplier
import com.github.weisj.jsvg.parser.impl.SVGDocumentBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.xml.stream.XMLStreamConstants

@Internal
fun createJSvgDocument(inputStream: InputStream): ParsedSvgDocument {
  return createJSvgDocument(createXmlStreamReader(inputStream))
}

@Internal
fun createJSvgDocument(data: ByteArray): ParsedSvgDocument = createJSvgDocument(createXmlStreamReader(data))

typealias AttributeMutator = (MutableMap<String, String>) -> Unit

@Internal
fun createJSvgDocument(xmlStreamReader: XMLStreamReader2, attributeMutator: AttributeMutator? = null): ParsedSvgDocument {
  try {
    return buildDocument(xmlStreamReader, attributeMutator)
  }
  finally {
    xmlStreamReader.closeCompletely()
  }
}

private fun buildDocument(reader: XMLStreamReader2, attributeMutator: AttributeMutator?): ParsedSvgDocument {
  if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
    throw IOException("Incorrect state: ${reader.eventType}")
  }

  val rootAttributes = RootAttributeCollector()
  // The default LoaderContext already denies external resources and accepts embedded data URIs,
  // which matches IDEA's historical "data URI only" rule.
  val loaderContext = MutableLoaderContext.createDefault()
    .preProcessor(rootAttributes)
    .build()
  val builder = SVGDocumentBuilder(/* rootURI = */ null, loaderContext, NODE_SUPPLIER)
  builder.startDocument()

  pushRootElement(reader, builder)
  streamFragment(reader, builder, attributeMutator)
  builder.endDocument()

  val svgDocument = builder.build()
  return ParsedSvgDocument(
    document = svgDocument,
    isDataScaled = rootAttributes.isDataScaled,
    rawWidth = rootAttributes.rawWidth,
    rawHeight = rootAttributes.rawHeight,
    rawViewBox = rootAttributes.rawViewBox,
  )
}

/**
 * Advances [reader] until the root `<svg>` element and pushes it into [builder].
 *
 * Matches the strict behavior of the legacy IDEA parser: the first start-element must be `svg`
 * (any other element, or character data, is a hard error). The root's attributes are passed to
 * the builder untouched — the caller-supplied [AttributeMutator] is applied only to descendants
 * to preserve the historical contract.
 */
private fun pushRootElement(reader: XMLStreamReader2, builder: SVGDocumentBuilder) {
  while (reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val localName = reader.localName
        if (localName != "svg") {
          throw IOException("Root element does not match that requested:\nRequested: svg\nFound: $localName")
        }
        val attributes = readAttributes(reader, attributeMutator = null)
        builder.startElement(qualifiedName(reader.prefix, localName), attributes)
        return
      }
      XMLStreamConstants.DTD,
      XMLStreamConstants.COMMENT,
      XMLStreamConstants.PROCESSING_INSTRUCTION,
      XMLStreamConstants.SPACE -> {
        // ignore
      }
      XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          throw IOException("Unexpected XMLStream event at document level: CHARACTERS (${reader.text})")
        }
      }
      else -> throw IOException("Unexpected XMLStream event at document level: ${reader.eventType}")
    }
  }
  throw IOException("Unexpected end-of-XMLStreamReader")
}

/**
 * Streams XML events from [reader] into [builder] until the document end is reached.
 *
 * Mirrors the contract of the upstream `StaxSVGLoader` parse loop but drives the builder
 * directly from a [XMLStreamReader2] cursor to avoid per-event allocations of the
 * `XMLEventReader` API used by [com.github.weisj.jsvg.parser.SVGLoader].
 */
private fun streamFragment(reader: XMLStreamReader2, builder: SVGDocumentBuilder, attributeMutator: AttributeMutator?) {
  while (reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val tagName = toLowerCaseTag(reader.localName)
        val attributes = readAttributes(reader, attributeMutator)
        val handled = builder.startElement(qualifiedName(reader.prefix, tagName), attributes)
        if (!handled) {
          // jsvg doesn't recognize this tag (unknown element or non-SVG namespace) — skip the
          // whole subtree. Mirrors StaxSVGLoader's handling so we stay forgiving about oddities.
          reader.skipElement()
          LOG.warn("unsupported $tagName")
        }
      }
      XMLStreamConstants.END_ELEMENT -> {
        builder.endElement(qualifiedName(reader.prefix, toLowerCaseTag(reader.localName)))
      }
      XMLStreamConstants.CDATA -> feedText(reader, builder)
      XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) feedText(reader, builder)
      }
      XMLStreamConstants.ENTITY_REFERENCE,
      XMLStreamConstants.COMMENT,
      XMLStreamConstants.PROCESSING_INSTRUCTION -> {
        // ignore
      }
      XMLStreamConstants.END_DOCUMENT -> return
      else -> throw IOException("Unexpected XMLStream event: ${reader.eventType}")
    }
  }
}

private fun feedText(reader: XMLStreamReader2, builder: SVGDocumentBuilder) {
  val start = reader.textStart
  builder.addTextContent(reader.textCharacters, start, start + reader.textLength)
}

private fun readAttributes(reader: XMLStreamReader2, attributeMutator: AttributeMutator?): MutableMap<String, String> {
  val attributeCount = reader.attributeCount
  if (attributeCount == 0 && attributeMutator == null) {
    return mutableMapOf()
  }
  val attributes = HashMap<String, String>(attributeCount)
  for (i in 0 until attributeCount) {
    val qualifiedName = qualifiedName(reader.getAttributePrefix(i), reader.getAttributeLocalName(i))
    attributes.put(qualifiedName, reader.getAttributeValue(i).trim())
  }
  attributeMutator?.invoke(attributes)
  return attributes
}

private fun qualifiedName(prefix: String?, localName: String): String {
  return if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"
}

private fun toLowerCaseTag(localName: String): String = localName.lowercase(Locale.ROOT)

// NodeSupplier is stateless aside from its internal map — safe to share across parses.
private val NODE_SUPPLIER = NodeSupplier()

private val LOG: Logger
  get() = Logger.getInstance("com.intellij.ui.svg.JSvgDocumentFactory")
