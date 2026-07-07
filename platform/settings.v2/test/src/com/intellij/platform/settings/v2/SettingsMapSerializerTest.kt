// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks the keys over string-keyed maps that the [Serializer.mapOf] combinator declares. A value can be non-null, or
 * nullable through [nullable], and the map itself can be nullable. Each key falls back to its default when it has no
 * value, and makes a round trip of a written value. The nullable map also makes a round trip of a stored `null`.
 */
@TestApplication
class SettingsMapSerializerTest {
  companion object {
    private val ints = SettingsKey("test.map.ints", default = emptyMap(), serializer = IntSerializer.mapOf())
    private val nullableValues = SettingsKey("test.map.nullableValues", default = emptyMap(), serializer = IntSerializer.nullable().mapOf())
    private val nullableMap = SettingsKey("test.map.nullableMap", default = null, serializer = IntSerializer.mapOf().nullable())
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `a map of non-null values defaults empty and round-trips`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals(emptyMap<String, Int>(), ints[context])

    ints[context] = mapOf("editor" to 14, "terminal" to 12)
    assertEquals(mapOf("editor" to 14, "terminal" to 12), ints[context])
  }

  @Test
  fun `a map of nullable values round-trips nulls among values`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    nullableValues[context] = mapOf("editor" to 14, "terminal" to null)
    assertEquals(mapOf("editor" to 14, "terminal" to null), nullableValues[context])
  }

  @Test
  fun `a nullable map defaults null and round-trips both a value and an explicit null`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals(null, nullableMap[context])

    nullableMap[context] = mapOf("editor" to 14)
    assertEquals(mapOf("editor" to 14), nullableMap[context])

    nullableMap[context] = null
    assertEquals(null, nullableMap[context])
  }
}
