// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/setting-values.spec.md

import com.intellij.platform.settings.v2.Serializer
import com.intellij.platform.settings.v2.SettingsValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Returns a [Serializer] for an arbitrary `@Serializable` type [T], so an API user does not write one by hand
 * (ADR 0118). The compiler-generated [KSerializer] encodes and decodes the value as a [Json] tree. [SettingsValueJson]
 * maps that tree to and from the [SettingsValue] model. No runtime reflection is involved. Nested objects, nullable
 * fields, lists with nulls, and maps with null values all round-trip.
 *
 * A polymorphic or sealed value carries the class discriminator of kotlinx inside its own object value. The
 * top-level `keyIdentity: value` layout of a config file does not change.
 */
@Experimental
inline fun <reified T> kotlinxSerializer(): Serializer<T> = kotlinxSerializer(serializer())

/** Returns a [Serializer] that bridges the given kotlinx [serializer]. See the reified [kotlinxSerializer] overload. */
@Experimental
fun <T> kotlinxSerializer(serializer: KSerializer<T>): Serializer<T> = KSerializerAdapter(serializer)

private val objectJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

private class KSerializerAdapter<T>(private val delegate: KSerializer<T>) : Serializer<T> {
  override fun serialize(value: T): SettingsValue = SettingsValueJson.fromElement(objectJson.encodeToJsonElement(delegate, value))

  /**
   * Decodes [value] through the kotlinx [delegate].
   *
   * kotlinx reports a value that does not fit [T] with [IllegalArgumentException], and its
   * `SerializationException` is a subtype of it. This method rethrows it as the [ClassCastException] that
   * [Serializer.deserialize] uses for a type mismatch, so the key resolves to its default.
   *
   * @throws ClassCastException if [value] does not match the shape of [T]
   */
  override fun deserialize(value: SettingsValue): T =
    try {
      objectJson.decodeFromJsonElement(delegate, SettingsValueJson.toElement(value))
    }
    catch (e: IllegalArgumentException) {
      throw ClassCastException("Stored value does not match ${delegate.descriptor.serialName}: ${e.message}").apply { initCause(e) }
    }
}
