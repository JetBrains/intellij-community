// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/file-layers.spec.md

import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsValue.BooleanValue
import com.intellij.platform.settings.v2.SettingsValue.DoubleValue
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.LongValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.NullValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Maps the storage-neutral [SettingsValue] tree to and from a `kotlinx.serialization` [JsonElement] tree. Both
 * [JsonSettingsCodec] and [kotlinxSerializer] use it. The codec renders the element as text, and the serializer
 * encodes and decodes a `@Serializable` value through it.
 *
 * [fromElement] narrows a JSON number to the smallest of int, long and double that holds it. So the wrapper of a
 * number can change on a round-trip. That does not matter: [toElement] gives the same JSON number for each wrapper,
 * and the consuming serializer coerces it to the type of its field.
 */
internal object SettingsValueJson {
  /** Returns the [JsonElement] tree of [value]. */
  fun toElement(value: SettingsValue): JsonElement = when (value) {
    is MapValue -> JsonObject(value.values.mapValues { toElement(it.value) })
    is ListValue -> JsonArray(value.items.map(::toElement))
    is StringValue -> JsonPrimitive(value.value)
    is IntValue -> JsonPrimitive(value.value)
    is LongValue -> JsonPrimitive(value.value)
    is DoubleValue -> JsonPrimitive(value.value)
    is BooleanValue -> JsonPrimitive(value.value)
    NullValue -> JsonNull
  }

  /** Returns the [SettingsValue] tree of [element]. A JSON primitive that is no boolean and no number becomes a string. */
  fun fromElement(element: JsonElement): SettingsValue = when (element) {
    is JsonObject -> MapValue.wrapOwned(element.mapValues { fromElement(it.value) })
    is JsonArray -> ListValue.wrapOwned(element.map(::fromElement))
    JsonNull -> NullValue
    is JsonPrimitive ->
      if (element.isString) StringValue(element.content)
      else element.booleanOrNull?.let(::BooleanValue)
           ?: element.intOrNull?.let(::IntValue)
           ?: element.longOrNull?.let(::LongValue)
           ?: element.doubleOrNull?.let(::DoubleValue)
           ?: StringValue(element.content)
  }
}
