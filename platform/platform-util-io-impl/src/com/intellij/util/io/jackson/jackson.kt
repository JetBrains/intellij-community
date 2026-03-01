// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JacksonUtil")
@file:ApiStatus.Internal
package com.intellij.util.io.jackson

import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.PrettyPrinter
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream
import java.io.Writer

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

fun JsonGenerator.writeFieldName(name: String): JsonGenerator = writeName(name)

fun JsonGenerator.writeStringField(fieldName: String, value: String?): JsonGenerator = writeStringProperty(fieldName, value)

fun JsonGenerator.writeBooleanField(fieldName: String, value: Boolean): JsonGenerator = writeBooleanProperty(fieldName, value)

fun JsonGenerator.writeNumberField(fieldName: String, value: Int): JsonGenerator = writeNumberProperty(fieldName, value)

fun JsonGenerator.writeNumberField(fieldName: String, value: Long): JsonGenerator = writeNumberProperty(fieldName, value)

fun JsonFactory.createGenerator(output: OutputStream, prettyPrinter: PrettyPrinter): JsonGenerator {
  return createGenerator(PrettyPrinterWriteContext(this, prettyPrinter), output)
}

fun JsonFactory.createGenerator(output: Writer, prettyPrinter: PrettyPrinter): JsonGenerator {
  return createGenerator(PrettyPrinterWriteContext(this, prettyPrinter), output)
}

private class PrettyPrinterWriteContext(
  private val factory: JsonFactory,
  private val prettyPrinter: PrettyPrinter,
) : ObjectWriteContext.Base() {
  override fun tokenStreamFactory() = factory

  override fun getPrettyPrinter() = prettyPrinter
}

@ApiStatus.Internal
open class IntelliJPrettyPrinter : DefaultPrettyPrinter() {
  companion object {
    @JvmField val UNIX_LINE_FEED_INSTANCE: DefaultIndenter = DefaultIndenter("  ", "\n")
  }

  init {
    _objectIndenter = UNIX_LINE_FEED_INSTANCE
  }
}
