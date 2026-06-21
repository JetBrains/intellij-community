// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory

internal fun JsonFactory.parseJsonObject(text: String): Map<String, Any?>? {
  createJsonParser(text).use { parser ->
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }
    return parser.readObjectValue()
  }
}

private fun JsonParser.readJsonValue(): Any? {
  return when (currentToken()) {
    JsonToken.START_OBJECT -> readObjectValue()
    JsonToken.START_ARRAY -> readArrayValue()
    JsonToken.VALUE_STRING -> string
    JsonToken.VALUE_NUMBER_INT -> longValue
    JsonToken.VALUE_NUMBER_FLOAT -> doubleValue
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> booleanValue
    JsonToken.VALUE_NULL -> null
    else -> {
      skipChildren()
      null
    }
  }
}

private fun JsonParser.readObjectValue(): Map<String, Any?> {
  val result = LinkedHashMap<String, Any?>()
  while (true) {
    val token = nextToken() ?: return result
    if (token == JsonToken.END_OBJECT) {
      return result
    }
    if (token != JsonToken.PROPERTY_NAME) {
      skipChildren()
      continue
    }
    val fieldName = currentName()
    if (nextToken() == null) {
      return result
    }
    result[fieldName] = readJsonValue()
  }
}

private fun JsonParser.readArrayValue(): List<Any?> {
  val result = mutableListOf<Any?>()
  while (true) {
    val token = nextToken() ?: return result
    if (token == JsonToken.END_ARRAY) {
      return result
    }
    result += readJsonValue()
  }
}

internal fun Map<*, *>.stringValue(key: String): String? {
  return when (val value = this[key]) {
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> null
  }
}

internal fun Map<*, *>.objectValue(key: String): Map<*, *>? {
  return this[key] as? Map<*, *>
}

internal fun Map<*, *>.listValue(key: String): List<*>? {
  return this[key] as? List<*>
}

internal fun Map<*, *>.stringListValue(key: String): List<String> {
  return listValue(key)
           ?.mapNotNull { value ->
             when (value) {
               is String -> value
               is Number, is Boolean -> value.toString()
               else -> null
             }
           }
         ?: emptyList()
}

internal fun Map<*, *>.intValue(key: String): Int? {
  return when (val value = this[key]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
    else -> null
  }
}

internal fun Map<*, *>.booleanValue(key: String, allowYes: Boolean = false, trimString: Boolean = false): Boolean? {
  return when (val value = this[key]) {
    is Boolean -> value
    is String -> {
      val text = if (trimString) value.trim() else value
      when {
        text.equals("true", ignoreCase = true) -> true
        text.equals("false", ignoreCase = true) -> false
        allowYes && text.equals("yes", ignoreCase = true) -> true
        else -> null
      }
    }
    else -> null
  }
}

internal fun String.padBase64Url(): String {
  val padding = (4 - length % 4) % 4
  return this + "=".repeat(padding)
}
