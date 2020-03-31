// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter

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
    @JvmField
    val UNIX_LINE_FEED_INSTANCE = DefaultIndenter("  ", "\n")
  }

  init {
    _objectFieldValueSeparatorWithSpaces = ": "
    _objectIndenter = UNIX_LINE_FEED_INSTANCE
    _arrayIndenter = UNIX_LINE_FEED_INSTANCE
  }
}