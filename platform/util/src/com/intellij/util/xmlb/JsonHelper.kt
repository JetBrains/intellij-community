// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LoggingSimilarMessage")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.MutableAccessor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.*

internal fun toJson(bean: Any, accessor: Accessor, converter: Converter<Any>?): JsonElement? {
  val value = accessor.read(bean) ?: return null
  if (converter == null) {
    return primitiveToJsonElement(value)
  }
  else {
    //return toJsonStringLiteral(converter.toString(value) ?: return null)
    return JsonPrimitive(converter.toString(value) ?: return null)
  }
}

internal fun primitiveToJsonElement(value: Any): JsonElement {
  return when (value) {
    is Date -> JsonPrimitive(value.time)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is String -> JsonPrimitive(JDOMUtil.removeControlChars(value))
    //else -> toJsonStringLiteral(value)
    else -> JsonPrimitive(value.toString())
  }
}

//private fun toJsonStringLiteral(value: Any): String {
//  val s = value.toString()
//  val builder = StringBuilder(s.length + 2)
//  builder.append('"')
//  StringUtil.escapeStringCharacters(s.length, s, "\"", builder)
//  builder.append('"')
//  return builder.toString()
//}

internal fun fromJson(bean: Any, data: JsonElement, accessor: MutableAccessor, valueClass: Class<*>, converter: Converter<Any>?) {
  val s = when {
    data === JsonNull -> null
    data is JsonPrimitive -> data.content
    else -> {
      LOG.warn("JsonPrimitive is expected but got $data")
      return
    }
  }

  if (converter == null) {
    XmlSerializerImpl.doSet(bean, s, accessor, valueClass)
  }
  else {
    accessor.set(bean, s?.let { converter.fromString(s) })
  }
}

internal fun fromJsonPrimitive(data: JsonElement): Any? {
  return when (data) {
    is JsonNull -> null
    is JsonPrimitive -> data.content
    else -> {
      LOG.warn("JsonPrimitive is expected but got $data")
      return Unit
    }
  }
}