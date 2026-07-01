// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/file-layers.spec.md

import com.intellij.platform.settings.v2.SettingsValue.MapValue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Converts a settings [MapValue] to and from the text of a config file. A user can edit the on-disk form by hand. It is
 * a plain `keyIdentity: value` mapping, and nested maps and lists use the native form of the format, with no type tags
 * or wrapper objects. A scalar round-trips as a string, a number or a boolean. A richer value is composed from
 * [MapValue] and [ListValue][com.intellij.platform.settings.v2.SettingsValue.ListValue].
 */
internal interface SettingsFileCodec {
  /**
   * Parses [text] into the map that the config holds. A blank text gives an empty map.
   *
   * @throws SettingsDecodingException when [text] is malformed (e.g. a half-written file) or its top level is not a
   *   map (ADR 0145)
   */
  fun decode(text: String): MapValue

  /** Returns the config text that holds [map]. */
  fun encode(map: MapValue): String
}

/**
 * Signals that the text given to [SettingsFileCodec.decode] does not parse. It wraps the failure of the codec
 * implementation, so a caller catches this one expected type instead of the library exceptions of the codec. A
 * catch of a broad `Exception` would also swallow the control-flow exceptions.
 */
internal class SettingsDecodingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Implements [SettingsFileCodec] for JSON over the [JsonElement] tree of `kotlinx.serialization`, so the file stays a
 * clean object literal.
 */
internal object JsonSettingsCodec : SettingsFileCodec {
  private val json = Json {
    prettyPrint = true
    isLenient = true
  }

  override fun decode(text: String): MapValue {
    if (text.isBlank()) return MapValue(emptyMap())
    val element = try {
      json.parseToJsonElement(text)
    }
    catch (e: SerializationException) {
      throw SettingsDecodingException("Malformed settings JSON", e)
    }
    return SettingsValueJson.fromElement(element) as? MapValue
           ?: throw SettingsDecodingException("Settings JSON top level is not an object")
  }

  override fun encode(map: MapValue): String = json.encodeToString(JsonElement.serializer(), SettingsValueJson.toElement(map))
}
