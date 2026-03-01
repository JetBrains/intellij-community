// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.common

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken

internal inline fun forEachJsonObjectField(parser: JsonParser, onField: (String) -> Boolean) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_OBJECT) return
    if (token != JsonToken.FIELD_NAME) {
      parser.skipChildren()
      continue
    }
    val fieldName = parser.currentName()
    if (parser.nextToken() == null) return
    if (!onField(fieldName)) return
  }
}

internal fun readJsonStringOrNull(parser: JsonParser): String? {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> parser.text
    JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> parser.numberValue.toString()
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> parser.booleanValue.toString()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

internal fun readJsonLongOrNull(parser: JsonParser): Long? {
  return when (parser.currentToken) {
    JsonToken.VALUE_NUMBER_INT -> parser.longValue
    JsonToken.VALUE_NUMBER_FLOAT -> parser.doubleValue.toLong()
    JsonToken.VALUE_STRING -> parser.text.toLongOrNull() ?: parser.text.toDoubleOrNull()?.toLong()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}
