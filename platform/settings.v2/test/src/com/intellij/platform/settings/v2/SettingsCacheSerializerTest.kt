// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.platform.settings.v2.SettingsValue.BooleanValue
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.NullValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.cache.CachedLayer
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks that [SettingsCacheSerializer] makes a round trip of the cached layers, with a nested polymorphic [MapValue]
 * tree. On garbage input it returns `null` and does not throw, because the cache is a pure accelerator (ADR 0083).
 */
class SettingsCacheSerializerTest {
  @Test
  fun `round-trips cached layers with a nested polymorphic value tree`() {
    val value = MapValue(mapOf(
      "s" to StringValue("hi"),
      "i" to IntValue(42),
      "b" to BooleanValue(true),
      "n" to NullValue,
      "list" to ListValue(StringValue("a"), IntValue(1)),
      "nested" to MapValue(mapOf("inner" to StringValue("deep"))),
    ))
    val layers = listOf(
      CachedLayer(contextId = "app", providerId = "com.example.AppProvider", locality = "LOCAL", value = value),
      CachedLayer(contextId = "file|hash|file:///root|sub/x", providerId = "com.example.FileProvider", locality = "TEAM",
                  value = MapValue(mapOf("k" to StringValue("v")))),
    )

    val decoded = SettingsCacheSerializer.decode(SettingsCacheSerializer.encode(layers))

    assertEquals(layers, decoded)
  }

  @Test
  fun `decodes an empty layer set`() {
    assertEquals(emptyList<CachedLayer>(), SettingsCacheSerializer.decode(SettingsCacheSerializer.encode(emptyList())))
  }

  @Test
  fun `returns null on garbage bytes instead of throwing`() {
    assertNull(SettingsCacheSerializer.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
  }
}
