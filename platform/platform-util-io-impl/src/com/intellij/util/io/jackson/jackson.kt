// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JacksonUtil")
@file:ApiStatus.Internal
package com.intellij.util.io.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import org.jetbrains.annotations.ApiStatus

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

@ApiStatus.Internal
open class IntelliJPrettyPrinter : DefaultPrettyPrinter() {
  companion object {
    @JvmField val UNIX_LINE_FEED_INSTANCE: DefaultIndenter = DefaultIndenter("  ", "\n")
  }

  init {
    _objectFieldValueSeparatorWithSpaces = ": "
    _objectIndenter = UNIX_LINE_FEED_INSTANCE
  }
}
