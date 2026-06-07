// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
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

internal val JsonParser.currentToken: JsonToken?
  get() = currentToken()

internal fun JsonGenerator.writeFieldName(name: String) {
  writeName(name)
}

internal fun JsonGenerator.writeStringField(name: String, value: String?) {
  writeStringProperty(name, value)
}

internal fun JsonGenerator.writeBooleanField(name: String, value: Boolean) {
  writeBooleanProperty(name, value)
}

internal fun JsonGenerator.writeNumberField(name: String, value: Int) {
  writeNumberProperty(name, value)
}

internal fun JsonGenerator.writeNullField(name: String) {
  writeNullProperty(name)
}
