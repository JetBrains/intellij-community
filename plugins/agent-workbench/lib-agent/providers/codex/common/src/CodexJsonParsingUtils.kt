// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import com.intellij.agent.workbench.json.forEachJsonObjectField as forEachWorkbenchJsonObjectField

inline fun forEachObjectField(parser: JsonParser, onField: (String) -> Boolean) {
  forEachWorkbenchJsonObjectField(parser, onField)
}

fun readStringOrNull(parser: JsonParser): String? {
  return readJsonStringOrNull(parser)
}

fun readLongOrNull(parser: JsonParser): Long? {
  return readJsonLongOrNull(parser)
}

val JsonParser.currentToken: JsonToken?
  get() = currentToken()

fun JsonGenerator.writeFieldName(name: String) {
  writeName(name)
}

inline fun JsonGenerator.writeObject(writeContent: JsonGenerator.() -> Unit) {
  writeStartObject()
  writeContent()
  writeEndObject()
}

inline fun JsonGenerator.writeObjectField(name: String, writeContent: JsonGenerator.() -> Unit) {
  writeFieldName(name)
  writeObject(writeContent)
}

inline fun JsonGenerator.writeArrayField(name: String, writeContent: JsonGenerator.() -> Unit) {
  writeFieldName(name)
  writeStartArray()
  writeContent()
  writeEndArray()
}

fun JsonGenerator.writeStringField(name: String, value: String?) {
  writeStringProperty(name, value)
}

fun JsonGenerator.writeStringArrayField(name: String, values: Iterable<String>) {
  writeArrayField(name) {
    values.forEach { value -> writeString(value) }
  }
}

fun JsonGenerator.writeStringArrayField(name: String, vararg values: String) {
  writeStringArrayField(name, values.asList())
}

fun JsonGenerator.writeBooleanField(name: String, value: Boolean) {
  writeBooleanProperty(name, value)
}

fun JsonGenerator.writeNumberField(name: String, value: Int) {
  writeNumberProperty(name, value)
}

fun JsonGenerator.writeNullField(name: String) {
  writeNullProperty(name)
}
