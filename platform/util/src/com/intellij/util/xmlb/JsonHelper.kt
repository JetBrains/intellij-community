// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LoggingSimilarMessage")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.MutableAccessor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.Date

internal fun toJson(bean: Any, accessor: Accessor, converter: Converter<Any>?): JsonElement {
  val value = accessor.read(bean) ?: return JsonNull
  if (converter == null) {
    return primitiveToJsonElement(value)
  }
  else {
    return JsonPrimitive(converter.toString(value) ?: return JsonNull)
  }
}

internal fun valueToJson(value: String?, valueClass: Class<*>): JsonElement? {
  try {
    return when {
      value == null -> JsonNull
      valueClass === Int::class.javaPrimitiveType || valueClass === Int::class.java || valueClass === java.lang.Integer::class.java -> JsonPrimitive(value.toInt())
      valueClass === Boolean::class.javaPrimitiveType || valueClass === Boolean::class.java || valueClass === java.lang.Boolean::class.java -> JsonPrimitive(value.toBoolean())
      valueClass === Double::class.javaPrimitiveType || valueClass === Double::class.java || valueClass === java.lang.Double::class.java -> JsonPrimitive(value.toDouble())
      valueClass === Float::class.javaPrimitiveType || valueClass === Float::class.java || valueClass === java.lang.Float::class.java -> JsonPrimitive(value.toFloat())
      valueClass === Long::class.javaPrimitiveType || valueClass === Long::class.java || valueClass === java.lang.Long::class.java || Date::class.java.isAssignableFrom(valueClass) -> JsonPrimitive(value.toLong())
      valueClass === Short::class.javaPrimitiveType || valueClass === Short::class.java || valueClass === java.lang.Short::class.java -> JsonPrimitive(value.toShort())
      else -> JsonPrimitive(value)
    }
  }
  catch (ignore: NumberFormatException) {
    return null
  }
}

internal fun primitiveToJsonElement(value: Any): JsonElement {
  return when (value) {
    is Date -> JsonPrimitive(value.time)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is String -> JsonPrimitive(JDOMUtil.removeControlChars(value))
    else -> JsonPrimitive(value.toString())
  }
}

internal fun setFromJson(bean: Any, data: JsonElement, accessor: MutableAccessor, valueClass: Class<*>, converter: Converter<Any>?) {
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

internal fun fromJsonPrimitive(data: JsonElement, valueClass: Class<*>): Any? {
  // yes, exception, the policy is "all or nothing", do no return semi-correct state
  return if (data is JsonNull) null else XmlSerializerImpl.convert(data.jsonPrimitive.content, valueClass)
}