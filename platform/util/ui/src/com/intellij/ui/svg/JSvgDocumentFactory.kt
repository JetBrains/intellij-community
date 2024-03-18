// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.AttributeParser
import com.github.weisj.jsvg.attributes.paint.DefaultPaintParser
import com.github.weisj.jsvg.nodes.SVG
import com.github.weisj.jsvg.nodes.Style
import com.github.weisj.jsvg.parser.*
import com.github.weisj.jsvg.parser.css.StyleSheet
import com.github.weisj.jsvg.parser.css.impl.SimpleCssParser
import com.github.weisj.jsvg.util.ResourceUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.io.InputStream
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

@Internal
fun createJSvgDocument(inputStream: InputStream): SVG {
  return createJSvgDocument(createXmlStreamReader(inputStream))
}

@Internal
fun createJSvgDocument(data: ByteArray): SVG = createJSvgDocument(createXmlStreamReader(data))

typealias AttributeMutator = (MutableMap<String, String>) -> Unit

@Internal
fun createJSvgDocument(xmlStreamReader: XMLStreamReader2, attributeMutator: AttributeMutator? = null): SVG {
  try {
    return buildDocument(xmlStreamReader, attributeMutator)
  }
  finally {
    xmlStreamReader.closeCompletely()
  }
}

private fun buildDocument(reader: XMLStreamReader2, attributeMutator: AttributeMutator?): SVG {
  var state = reader.eventType
  if (XMLStreamConstants.START_DOCUMENT != state) {
    throw IOException("Incorrect state: $state")
  }

  var root: ParsedElement? = null

  val styleElements = mutableListOf<Style>()
  val styleSheets = mutableListOf<StyleSheet>()

  while (state != XMLStreamConstants.END_DOCUMENT) {
    when (state) {
      XMLStreamConstants.START_DOCUMENT -> {
        assert(root == null)
      }
      XMLStreamConstants.DTD, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION, XMLStreamConstants.SPACE -> {
      }
      XMLStreamConstants.START_ELEMENT -> {
        val localName = reader.localName
        if (localName != "svg") {
          throw IOException("Root element does not match that requested:\nRequested: svg\nFound: $localName")
        }

        val namedElements = HashMap<String, ParsedElement>()
        val attributes = readAttributes(reader = reader, attributeMutator = null)

        root = ParsedElement(
          attributes.get("id"),
          AttributeNode(/* tagName = */ getQualifiedName(reader.prefix, localName),
                        /* attributes = */ attributes,
                        /* parent = */ null,
                        /* namedElements = */ namedElements,
                        /* styleSheets = */ styleSheets,
                        /* loadHelper = */ jsvgLoadHelper), SVG()
        )
        processElementFragment(reader = reader,
                               root = root,
                               namedElements = namedElements,
                               attributeMutator = attributeMutator,
                               styleSheets = styleSheets,
                               styleElements = styleElements)
      }
      XMLStreamConstants.CHARACTERS -> {
        val badContent = reader.text
        if (!reader.isWhiteSpace) {
          throw IOException("Unexpected XMLStream event at Document level: CHARACTERS ($badContent)")
        }
      }
      else -> throw IOException("Unexpected XMLStream event at Document level:$state")
    }
    state = if (reader.hasNext()) {
      reader.next()
    }
    else {
      throw IOException("Unexpected end-of-XMLStreamReader")
    }
  }

  if (!styleElements.isEmpty()) {
    val cssParser = SimpleCssParser()
    for (styleElement in styleElements) {
      styleElement.parseStyleSheet(cssParser)
      styleSheets.add(styleElement.styleSheet())
    }
  }
  root!!.build()
  return root.node() as SVG
}

private val jsvgLoadHelper = LoadHelper(
  /* attributeParser = */ AttributeParser(DefaultPaintParser()),
  /* resourceLoader = */ ResourceLoader { uri ->
  if (uri.scheme == "data") {
    ValueUIFuture(ResourceUtil.loadImage(uri))
  }
  else {
    LOG.warn("Only data URI is allowed (uri=$uri)")
    null
  }
})

private val LOG: Logger
  get() = Logger.getInstance(NodeMap::class.java)

private val NODE_CONSTRUCTOR_MAP = NodeMap.createNodeConstructorMap(CollectionFactory.createCaseInsensitiveStringMap())

private fun processElementFragment(reader: XMLStreamReader2,
                                   root: ParsedElement,
                                   namedElements: MutableMap<String, ParsedElement>,
                                   attributeMutator: AttributeMutator?,
                                   styleSheets: List<StyleSheet>,
                                   styleElements: MutableList<Style>) {
  val queue = ArrayList<ParsedElement>()
  queue.add(root)
  while (queue.isNotEmpty() && reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val localName = reader.localName
        val nodeSupplier = NODE_CONSTRUCTOR_MAP.get(localName)
        if (nodeSupplier == null) {
          reader.skipElement()
          LOG.warn("unsupported $localName")
          continue
        }

        val svgNode = nodeSupplier.get()
        if (svgNode is Style) {
          styleElements.add(svgNode)
        }

        val parent = queue.last()

        val attributes = readAttributes(reader = reader, attributeMutator = attributeMutator)
        val parsedElement = ParsedElement(
          attributes.get("id"),
          AttributeNode(/* tagName = */ getQualifiedName(reader.prefix, localName),
                        /* attributes = */ attributes,
                        /* parent = */ parent.attributeNode(),
                        /* namedElements = */ namedElements,
                        /* styleSheets = */ styleSheets,
                        /* loadHelper = */ jsvgLoadHelper),
          svgNode,
        )
        parent.addChild(parsedElement)

        val id = parsedElement.id()
        if (id != null) {
          namedElements.putIfAbsent(id, parsedElement)
        }

        queue.add(parsedElement)
      }
      XMLStreamConstants.END_ELEMENT -> {
        queue.removeLast()
      }
      XMLStreamConstants.CDATA -> queue.last().node().addContent(getChars(reader))
      XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          queue.last().node().addContent(getChars(reader))
        }
      }
      XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
      }
      else -> throw IOException("Unexpected XMLStream event: ${reader.eventType}")
    }
  }
}

private fun getChars(reader: XMLStreamReader2): CharArray {
  val fromIndex = reader.textStart
  return reader.textCharacters.copyOfRange(fromIndex, fromIndex + reader.textLength)
}

private fun readAttributes(reader: XMLStreamReader, attributeMutator: AttributeMutator?): Map<String, String> {
  val attributeCount = reader.attributeCount
  if (attributeCount == 0) {
    return emptyMap()
  }

  val attributes = HashMap<String, String>(attributeCount)
  for (i in 0 until attributeCount) {
    val localName = reader.getAttributeLocalName(i)
    val prefix = reader.getAttributePrefix(i)
    val qualifiedName = getQualifiedName(prefix = prefix, localName = localName)
    attributes.put(qualifiedName, reader.getAttributeValue(i).trim())
  }

  attributeMutator?.invoke(attributes)
  return attributes
}

private fun getQualifiedName(prefix: String?, localName: String): String {
  return if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"
}