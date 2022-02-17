// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlDomReader")
@file:ApiStatus.Internal
package com.intellij.util.xml.dom

import com.fasterxml.aalto.WFCException
import com.fasterxml.aalto.impl.ErrorConsts
import com.intellij.util.xml.dom.createXmlStreamReader
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

private class XmlElementBuilder(@JvmField var name: String, @JvmField var attributes: Map<String, String>) {
  @JvmField var content: String? = null
  @JvmField val children: ArrayList<XmlElement> = ArrayList()
}

interface XmlInterner {
  fun name(value: String): String

  /**
   * [name] is interned.
   */
  fun value(name: String, value: String): String
}

object NoOpXmlInterner : XmlInterner {
  override fun name(value: String) = value

  override fun value(name: String, value: String) = value
}

@ApiStatus.Internal
fun readXmlAsModel(inputStream: InputStream): XmlElement {
  val reader = createXmlStreamReader(inputStream)
  try {
    val tag = nextTag(reader)
    val rootName = if (tag == XMLStreamConstants.START_ELEMENT) {
      reader.localName
    }
    else {
      null
    }
    return readXmlAsModel(reader, rootName, NoOpXmlInterner)
  }
  finally {
    reader.closeCompletely()
  }
}

@ApiStatus.Internal
fun readXmlAsModel(inputStream: ByteArray): XmlElement {
  val reader = createXmlStreamReader(inputStream)
  try {
    val tag = nextTag(reader)
    val rootName = if (tag == XMLStreamConstants.START_ELEMENT) {
      reader.localName
    }
    else {
      null
    }
    return readXmlAsModel(reader, rootName, NoOpXmlInterner)
  }
  finally {
    reader.close()
  }
}

@ApiStatus.Internal
fun readXmlAsModel(reader: XMLStreamReader2,
                   rootName: String?,
                   interner: XmlInterner): XmlElement {
  val fragment = XmlElementBuilder(name = if (rootName == null) "" else interner.name(rootName), attributes = readAttributes(reader = reader, interner = interner))
  var current = fragment
  val stack = ArrayDeque<XmlElementBuilder>()
  val elementPool = ArrayDeque<XmlElementBuilder>()
  var depth = 1
  while (reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val name = interner.name(reader.localName)
        val attributes = readAttributes(reader, interner = interner)
        if (reader.isEmptyElement) {
          current.children.add(XmlElement(name = name,
                                          attributes = attributes,
                                          children = Collections.emptyList(),
                                          content = null))
          reader.skipElement()
          continue
        }

        var child = elementPool.pollLast()
        if (child == null) {
          child = XmlElementBuilder(name = name, attributes = attributes)
        }
        else {
          child.name = name
          child.attributes = attributes
        }
        stack.addLast(current)
        current = child
        depth++
      }
      XMLStreamConstants.END_ELEMENT -> {
        val children: List<XmlElement>
        if (current.children.isEmpty()) {
          children = Collections.emptyList()
        }
        else {
          @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
          children = Arrays.asList(*current.children.toArray(arrayOfNulls<XmlElement>(current.children.size)))
          current.children.clear()
        }

        val result = XmlElement(name = current.name, attributes = current.attributes, children = children, content = current.content)
        current.content = null
        elementPool.addLast(current)

        depth--
        if (depth == 0) {
          return result
        }

        current = stack.removeLast()
        current.children.add(result)
      }
      XMLStreamConstants.CDATA -> {
        if (current.content == null) {
          current.content = interner.value(current.name, reader.text)
        }
        else {
          current.content += reader.text
        }
      }
      XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          if (current.content == null) {
            current.content = reader.text
          }
          else {
            current.content += reader.text
          }
        }
      }
      XMLStreamConstants.SPACE, XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
      }
      else -> throw XMLStreamException("Unexpected XMLStream event ${reader.eventType}", reader.location)
    }
  }

  throw XMLStreamException("Unexpected end of input: ${reader.eventType}", reader.location)
}

private fun readAttributes(reader: XMLStreamReader2, interner: XmlInterner): Map<String, String> {
  return when (val attributeCount = reader.attributeCount) {
    0 -> Collections.emptyMap()
    1 -> {
      val name = interner.name(reader.getAttributeLocalName(0))
      Collections.singletonMap(name, interner.value(name, reader.getAttributeValue(0)))
    }
    else -> {
      // Map.of cannot be used here - in core-impl only Java 8 is allowed
      @Suppress("SSBasedInspection")
      val result = Object2ObjectOpenHashMap<String, String>(attributeCount)
      var i = 0
      while (i < attributeCount) {
        val name = interner.name(reader.getAttributeLocalName(i))
        result[name] = interner.value(name, reader.getAttributeValue(i))
        i++
      }
      result
    }
  }
}

private fun nextTag(reader: XMLStreamReader2): Int {
  while (true) {
    val next = reader.next()
    when (next) {
      XMLStreamConstants.SPACE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION, XMLStreamConstants.DTD -> continue
      XMLStreamConstants.CDATA, XMLStreamConstants.CHARACTERS -> {
        if (reader.isWhiteSpace) {
          continue
        }
        throw WFCException("Received non-all-whitespace CHARACTERS or CDATA event in nextTag().", reader.location)
      }
      XMLStreamConstants.START_ELEMENT, XMLStreamConstants.END_ELEMENT -> return next
    }
    throw WFCException("Received event " + ErrorConsts.tokenTypeDesc(next) + ", instead of START_ELEMENT or END_ELEMENT.", reader.location)
  }
}