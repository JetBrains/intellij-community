// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jdom.*

private val factory = CBORFactory().configure(CBORGenerator.Feature.STRINGREF, true)

internal fun encodeXmlToCbor(element: Element): ByteArray {
  val b = BufferExposingByteArrayOutputStream()
  val out = factory.createGenerator(b)
  out.use {
    writeElement(element, out)
  }
  return b.toByteArray()
}

internal fun decodeCborToXml(input: ByteArray): Element {
  factory.createParser(input).use {
    assert(it.nextToken() == JsonToken.START_OBJECT)
    return readElement(it)
  }
}

private fun readElement(reader: CBORParser): Element {
  var field: String? = reader.nextFieldName()!!
  assert(field == "t")
  val element = Element(reader.nextTextValue()!!)

  while (true) {
    field = reader.nextFieldName()
    if (field == null) {
      assert(reader.currentToken() == JsonToken.END_OBJECT)
      break
    }

    if (field == "a") {
      readAttributes(reader, element)
    }
    else if (field == "c") {
      assert(reader.nextToken() == JsonToken.START_ARRAY)
      while (true) {
        when (val token = reader.nextToken()) {
          JsonToken.END_ARRAY -> break
          JsonToken.START_OBJECT -> {
            element.addContent(readElement(reader))
          }
          JsonToken.VALUE_STRING -> {
            element.addContent(Text(reader.valueAsString))
          }
          JsonToken.VALUE_FALSE -> {
            element.addContent(CDATA(reader.nextTextValue()!!))
          }
          else -> {
            throw IllegalStateException("Unexpected token: $token")
          }
        }
      }
    }
    else {
      logger<Element>().warn("Unknown property: $field")
      reader.nextToken()
      reader.skipChildren()
    }
  }
  return element
}

private fun readAttributes(reader: CBORParser, element: Element) {
  assert(reader.nextToken() == JsonToken.START_OBJECT)
  while (reader.nextToken() != JsonToken.END_OBJECT) {
    val name = reader.currentName
    val value = reader.nextTextValue()
    element.setAttribute(Attribute(name, value))
  }
}

private fun writeElement(element: Element, out: CBORGenerator) {
  out.writeStartObject()
  out.writeStringField("t", element.name)

  if (element.hasAttributes()) {
    writeAttributes(element.attributes, out)
  }

  if (element.content.isNotEmpty()) {
    out.writeFieldName("c")
    out.writeStartArray()
    for (item in element.content) {
      when (item) {
        is Element -> {
          writeElement(item, out)
        }
        is CDATA -> {
          if (item.text != null) {
            out.writeBoolean(false)
            out.writeString(item.text)
          }
        }
        is Text -> {
          val text = item.text
          if (text != null && !Verifier.isAllXMLWhitespace(text)) {
            out.writeString(item.text)
          }
        }
      }
    }
    out.writeEndArray()
  }

  out.writeEndObject()
}

private fun writeAttributes(attributes: List<Attribute>, out: CBORGenerator) {
  out.writeFieldName("a")
  out.writeStartObject(attributes.size)
  for (attribute in attributes) {
    out.writeStringField(attribute.name, attribute.value)
  }
  out.writeEndObject()
}