// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JacksonUtil")
package com.intellij.util.io.jackson

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import java.io.IOException
import java.io.Reader
import kotlin.jvm.Throws

inline fun JsonGenerator.obj(fieldName: String? = null, writer: () -> Unit) {
  fieldName?.let {
    writeFieldName(it)
  }
  writeStartObject()
  writer()
  writeEndObject()
}

inline fun JsonGenerator.array(fieldName: String? = null, writer: () -> Unit) {
  fieldName?.let {
    writeFieldName(it)
  }
  writeStartArray()
  writer()
  writeEndArray()
}

open class IntelliJPrettyPrinter : DefaultPrettyPrinter() {
  companion object {
    @JvmField val UNIX_LINE_FEED_INSTANCE: DefaultIndenter = DefaultIndenter("  ", "\n")
  }

  init {
    _objectFieldValueSeparatorWithSpaces = ": "
    _objectIndenter = UNIX_LINE_FEED_INSTANCE
  }
}

@Throws(IOException::class)
fun readSingleField(parser: JsonParser, name: String): String? {
  if (parser.nextToken() == JsonToken.START_OBJECT) {
    while (parser.nextToken() != null) {
      if (parser.currentName == name) {
        parser.nextToken()
        return parser.text
      }
      parser.skipChildren()
    }
  }
  return null
}
