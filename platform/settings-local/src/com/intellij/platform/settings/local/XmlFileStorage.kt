// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.fasterxml.aalto.UncheckedStreamException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SafeStAXStreamBuilder
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.createXmlStreamReader
import com.intellij.util.xml.dom.readXmlAsModel
import org.codehaus.stax2.XMLStreamReader2
import org.jdom.Element
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

private class Chunk(@JvmField var data: String) {
  @JvmField
  var modified: Boolean = false
}

internal class XmlFileStorage(private val file: Path) {
  private val map = SynchronizedClearableLazy { readChunks(file, sorted = false) }

  fun getJdom(key: String): Element? {
    val data = map.value.get(key) ?: return null
    try {
      val xmlStreamReader = createXmlStreamReader(StringReader(data.data))
      try {
        return SafeStAXStreamBuilder.build(/* stream = */ xmlStreamReader,
                                           /* isIgnoreBoundaryWhitespace = */ true,
                                           /* isNsSupported = */ false,
                                           /* factory = */ SafeStAXStreamBuilder.FACTORY)
      }
      finally {
        xmlStreamReader.close()
      }
    }
    catch (e: XMLStreamException) {
      thisLogger().warn(e)
    }
    catch (e: UncheckedStreamException) {
      thisLogger().warn(e)
    }
    return null
  }

  fun get(key: String): XmlElement? {
    val data = map.value.get(key) ?: return null
    try {
      return readXmlAsModel(StringReader(data.data))
    }
    catch (e: XMLStreamException) {
      thisLogger().warn(e)
    }
    catch (e: UncheckedStreamException) {
      thisLogger().warn(e)
    }
    return null
  }
}

private fun readChunks(file: Path, @Suppress("SameParameterValue") sorted: Boolean): MutableMap<String, Chunk> {
  val map: MutableMap<String, Chunk> = if (sorted) TreeMap() else HashMap()
  if (Files.notExists(file)) {
    return map
  }

  val content = Files.readString(file)
  val reader = createXmlStreamReader(StringReader(content))
  try {
    var token = reader.next()
    while (true) {
      when (token) {
        XMLStreamConstants.START_ELEMENT -> {
          val name = reader.localName
          if (name != "component") {
            if (reader.depth > 1) {
              reader.skipElement()
            }

            if (!reader.hasNext()) {
              break
            }
            token = reader.next()
            continue
          }

          val componentName = readComponentNameAttribute(reader)
          if (componentName.isNullOrEmpty()) {
            reader.skipElement()
            continue
          }

          val start = reader.location
          reader.skipElement()
          token = reader.next()
          val end = reader.location
          map.put(componentName, Chunk(content.substring(start.characterOffset, end.characterOffset)))
          continue
        }
        XMLStreamConstants.END_ELEMENT -> {
        }
        XMLStreamConstants.END_DOCUMENT -> {
          return map
        }
        XMLStreamConstants.CDATA -> {
        }
        XMLStreamConstants.CHARACTERS -> {
        }
        XMLStreamConstants.SPACE, XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
        }
        else -> {
          throw XMLStreamException("Unexpected XMLStream event ${reader.eventType}", reader.location)
        }
      }

      if (!reader.hasNext()) {
        break
      }
      token = reader.next()
    }

    throw XMLStreamException("Unexpected end of input: ${reader.eventType}", reader.location)
  }
  finally {
    reader.closeCompletely()
  }
}

private fun readComponentNameAttribute(reader: XMLStreamReader2): String? {
  val attributeCount = reader.attributeCount
  for (i in 0 until attributeCount) {
    if (reader.getAttributeLocalName(i) == "name") {
      return reader.getAttributeValue(i)
    }
  }
  return null
}