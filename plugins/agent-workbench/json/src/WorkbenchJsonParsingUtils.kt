// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.PrettyPrinter
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.TokenStreamFactory
import tools.jackson.core.json.JsonFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

fun JsonFactory.createJsonParser(content: String): JsonParser {
  return createParser(ObjectReadContext.empty(), content)
}

fun JsonFactory.createJsonParser(content: ByteArray): JsonParser {
  return createParser(ObjectReadContext.empty(), content)
}

fun JsonFactory.createJsonParser(input: InputStream): JsonParser {
  return createParser(ObjectReadContext.empty(), input)
}

fun JsonFactory.createJsonParser(reader: Reader): JsonParser {
  return createParser(ObjectReadContext.empty(), reader)
}

fun JsonFactory.createJsonGenerator(output: OutputStream): JsonGenerator {
  return createGenerator(ObjectWriteContext.empty(), output).configure(StreamWriteFeature.AUTO_CLOSE_TARGET, false)
}

fun JsonFactory.createJsonGenerator(writer: Writer): JsonGenerator {
  return createGenerator(ObjectWriteContext.empty(), writer).configure(StreamWriteFeature.AUTO_CLOSE_TARGET, false)
}

fun JsonFactory.createJsonGenerator(writer: Writer, prettyPrinter: PrettyPrinter): JsonGenerator {
  return createGenerator(PrettyPrinterWriteContext(this, prettyPrinter), writer).configure(StreamWriteFeature.AUTO_CLOSE_TARGET, false)
}

private class PrettyPrinterWriteContext(
  private val factory: JsonFactory,
  private val prettyPrinter: PrettyPrinter,
) : ObjectWriteContext.Base() {
  override fun tokenStreamFactory(): TokenStreamFactory = factory

  override fun getPrettyPrinter(): PrettyPrinter = prettyPrinter
}

inline fun forEachJsonObjectField(parser: JsonParser, onField: (String) -> Boolean) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_OBJECT) return
    if (token != JsonToken.PROPERTY_NAME) {
      parser.skipChildren()
      continue
    }
    val fieldName = parser.currentName()
    if (parser.nextToken() == null) return
    if (!onField(fieldName)) return
  }
}

fun readJsonStringOrNull(parser: JsonParser): String? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> parser.string
    JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> parser.numberValue.toString()
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> parser.booleanValue.toString()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

fun readJsonLongOrNull(parser: JsonParser): Long? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_NUMBER_INT -> parser.longValue
    JsonToken.VALUE_NUMBER_FLOAT -> parser.doubleValue.toLong()
    JsonToken.VALUE_STRING -> parser.string.toLongOrNull() ?: parser.string.toDoubleOrNull()?.toLong()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}
