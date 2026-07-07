// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the `null` values of a key. A key declared with no default has a nullable type and a `null` default. When no
 * layer supplies a value, the key resolves to that default. A non-null value resolves as usual, and a stored `null`
 * round-trips through [SettingsValue.NullValue] back to `null`. A key declared with no default and a non-nullable type
 * fails.
 *
 * With a non-null default, a stored `null` is a value, not an absent one. Each read path returns it and not the
 * default: a direct read, [readWhenReady], a read of a slotted child, and [SettingsKey.values].
 */
@TestApplication
class SettingsNullDefaultTest {
  companion object {
    private val optionalColor = SettingsKey<String?>("test.nd.color")
    private val shade = SettingsKey("test.nd.shade", default = "red", serializer = StringSerializer.nullable())
    private val paletteCurrent = SettingsKey("test.nd.palette.current", default = "default")
    private val paletteData = SettingsMapKey("test.nd.palette.data", discriminatorKey = paletteCurrent)
    private val paletteShade = SettingsKey("test.nd.palette.shade", default = "red", serializer = StringSerializer.nullable(),
                                           storageKey = paletteData)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `a null default resolves when unset, and a stored value or explicit null round-trips`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals(null, optionalColor[context]) // nothing stored → null default

    optionalColor[context] = "blue"
    assertEquals("blue", optionalColor[context])

    optionalColor[context] = null // explicit null stored as NullValue → deserializes back to null
    assertEquals(null, optionalColor[context])
  }

  @Test
  fun `a stored null reads as null, not as the non-null default`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals("red", shade[context])
    assertEquals(emptyList<String?>(), shade.values(context))

    shade[context] = null
    assertEquals(null, shade[context])
    assertEquals(null, context.readWhenReady(shade))
    assertEquals(listOf(null), shade.values(context))
  }

  @Test
  fun `a stored null in a slot reads as null, not as the non-null default`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val slotContext = SettingsContext.app().access { this[paletteCurrent] = "dark" }

    assertEquals("red", paletteShade[slotContext])

    paletteShade[slotContext] = null
    assertEquals(null, paletteShade[slotContext])
  }

  @Test
  fun `a key without a default rejects a non-nullable type`() {
    assertThrows<IllegalArgumentException> { SettingsKey<String>("test.nd.nonNullable") }
  }
}
