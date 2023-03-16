// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.AttributeParser
import com.github.weisj.jsvg.attributes.paint.DefaultPaintParser
import com.github.weisj.jsvg.nodes.SVG
import com.github.weisj.jsvg.nodes.Style
import com.github.weisj.jsvg.parser.*
import com.github.weisj.jsvg.util.ResourceUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

@Internal
fun createJSvgDocument(inputStream: InputStream): SVG {
  return createJSvgDocument(createXmlStreamReader(inputStream))
}

@Internal
fun createJSvgDocument(data: ByteArray): SVG = createJSvgDocument(createXmlStreamReader(data))

typealias AttributeMutator = (MutableMap<String, String>) -> Unit

internal fun createJSvgDocument(xmlStreamReader: XMLStreamReader2, attributeMutator: AttributeMutator? = null): SVG {
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
        val attributes = readAttributes(reader = reader, classSelectors = null, attributeMutator = null)
        root = ParsedElement(
          attributes.get("id"),
          AttributeNode(getQualifiedName(reader.prefix, localName), attributes, null, namedElements, JSvgLoadHelper), SVG()
        )
        processElementFragment(reader = reader, root = root, namedElements = namedElements, attributeMutator = attributeMutator)
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

  root!!.build()
  return root.node() as SVG
}

private object JSvgLoadHelper : LoadHelper {
  private val resourceLoader: ResourceLoader by lazy {
    ResourceLoader { uri ->
      if (uri.scheme == "data") {
        ValueUIFuture(ResourceUtil.loadImage(uri))
      }
      else {
        LOG.warn("Only data URI is allowed (uri=$uri)")
        null
      }
    }
  }

  private val attributeParser = AttributeParser(DefaultPaintParser())

  override fun attributeParser(): AttributeParser = attributeParser

  override fun resourceLoader(): ResourceLoader = resourceLoader
}

private val LOG: Logger
  get() = Logger.getInstance(JSvgLoadHelper::class.java)

private val NODE_CONSTRUCTOR_MAP = NodeMap.createNodeConstructorMap(CollectionFactory.createCaseInsensitiveStringMap())

private fun processElementFragment(reader: XMLStreamReader2,
                                   root: ParsedElement,
                                   namedElements: MutableMap<String, ParsedElement>,
                                   attributeMutator: AttributeMutator?) {
  var classSelectors: MutableMap<String, Map<String, ValueWithPriority>>? = null

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
          if (reader.next() == XMLStreamConstants.END_ELEMENT) {
            continue
          }

          classSelectors = HashMap()

          parseStylesheet(reader = reader, classSelectors = classSelectors)
          while (reader.next() != XMLStreamConstants.END_ELEMENT) {
            // skip the rest
          }
          continue
        }

        val parent = queue.last()

        val attributes = readAttributes(reader = reader, classSelectors = classSelectors, attributeMutator = attributeMutator)
        val parsedElement = ParsedElement(
          attributes.get("id"),
          AttributeNode(getQualifiedName(reader.prefix, localName), attributes, parent.attributeNode(), namedElements, JSvgLoadHelper),
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
      XMLStreamConstants.CDATA -> queue.last().node().addContent(reader.textCharacters.copyOf())
      XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          queue.last().node().addContent(reader.textCharacters.copyOf())
        }
      }
      XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
      }
      else -> throw IOException("Unexpected XMLStream event: ${reader.eventType}")
    }
  }
}

private fun parseStylesheet(reader: XMLStreamReader2, classSelectors: MutableMap<String, Map<String, ValueWithPriority>>) {
  val priorityCache = ConcurrentHashMap<String, Priority>()

  val rules = reader.text
    // remove newlines
    .replace("\n", "")
    // remove comments
    .replace("\\/\\*[^*]*\\*+([^/*][^*]*\\*+)*\\/", "")
  val statements = rules.splitToSequence('{', '}').map(String::trim).filterNot(String::isEmpty).iterator()
  while (statements.hasNext()) {
    val selectorString = statements.next()
    // the list of css styles for the selector
    assert(statements.hasNext())
    val properties = statements.next()
    val selectors = selectorString.splitToSequence(',').map(String::trim).filterNot(String::isEmpty)
    for (selector in selectors) {
      // support only "class selector"
      if (!selector.startsWith('.')) {
        LOG.warn("unsupported selector: $selector")
        continue
      }

      val className = selector.substring(1)

      classSelectors.merge(className, parseCssRule(getPriority(selector, priorityCache), properties), BiFunction(::mergeStyle))
    }
  }
}

private fun readAttributes(reader: XMLStreamReader,
                           classSelectors: Map<String, Map<String, ValueWithPriority>>?,
                           attributeMutator: AttributeMutator?): Map<String, String> {
  val attributeCount = reader.attributeCount
  if (attributeCount == 0) {
    return emptyMap()
  }

  val attributes = HashMap<String, String>(attributeCount)
  for (i in 0 until attributeCount) {
    val localName = reader.getAttributeLocalName(i)

    if (localName == "class") {
      for (className in reader.getAttributeValue(i).splitToSequence(' ').map(String::trim)) {
        for ((property, v) in (classSelectors?.get(className) ?: continue)) {
          attributes.put(property, v.value)
        }
      }
      continue
    }

    val prefix = reader.getAttributePrefix(i)
    val qualifiedName = getQualifiedName(prefix = prefix, localName = localName)
    attributes.put(qualifiedName, reader.getAttributeValue(i))
  }

  attributeMutator?.invoke(attributes)
  return attributes
}

private fun getQualifiedName(prefix: String?, localName: String): String {
  return if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"
}

private fun getPriority(selector: String, priorityCache: MutableMap<String, Priority>): Priority {
  return priorityCache.computeIfAbsent(selector, Function {
    var b = 0
    var c = 0
    var d = 0
    val pieces = selector.splitToSequence(' ').map(String::trim).filter { !it.isEmpty() }
    for (pc in pieces) {
      if (pc.startsWith('#')) {
        b++
        continue
      }
      if (pc.contains('[') || pc.startsWith('.') || pc.contains(':') && !pc.contains("::")) {
        c++
        continue
      }
      d++
    }
    Priority(a = 0, b = b, c = c, d = d)
  })
}

private fun parseCssRule(priority: Priority, properties: String?): Map<String, ValueWithPriority> {
  if (properties.isNullOrBlank()) {
    return emptyMap()
  }

  val props = properties.split(';')
  if (props.isEmpty()) {
    return emptyMap()
  }

  val result = LinkedHashMap<String, ValueWithPriority>()
  for (p in props) {
    val pcs = p.split(':')
    if (pcs.size != 2) {
      continue
    }

    val name = pcs[0].trim()
    val value = pcs[1].trim()
    result.put(name, ValueWithPriority(priority = priority, value = value))
  }
  return result
}

private fun mergeStyle(oldProps: Map<String, ValueWithPriority?>,
                       newProps: Map<String, ValueWithPriority?>): Map<String, ValueWithPriority> {
  val result = LinkedHashMap<String, ValueWithPriority>()
  val allProps = LinkedHashSet<String>(oldProps.size + newProps.size)
  allProps.addAll(oldProps.keys)
  allProps.addAll(newProps.keys)
  for (p in allProps) {
    val oldValue = oldProps.get(p)
    val newValue = newProps.get(p)
    if (oldValue == null) {
      result.put(p, newValue!!)
      continue
    }
    if (newValue == null) {
      result.put(p, oldValue)
      continue
    }

    val compare = oldValue.priority.compareTo(newValue.priority)
    result.put(p, if (compare < 0) newValue else oldValue)
  }
  return result
}

private data class Priority(@JvmField var a: Int, @JvmField var b: Int, @JvmField var c: Int, @JvmField var d: Int) : Comparable<Priority> {
  override operator fun compareTo(other: Priority): Int {
    if (other === this) {
      return 0
    }

    var result = a.compareTo(other.a)
    if (result != 0) {
      return result
    }

    result = b.compareTo(other.b)
    if (result != 0) {
      return result
    }

    result = c.compareTo(other.c)
    return if (result == 0) d.compareTo(other.d) else result
  }
}

private class ValueWithPriority(@JvmField val value: String, @JvmField val priority: Priority)