// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the write and the read of scalar keys through the vault, backed by a [TestLayerProvider]. An unset key reads
 * its default, and a written value reads back. A [readonly][SettingsKey.readonly] key reads its default even when a
 * layer holds a value (ADR 0011).
 */
@TestApplication
class SettingsResolutionTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `resolves defaults when nothing is stored, then the written value once saved`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    val intKey = SettingsKey("test.resolve.int", default = 7)
    val boolKey = SettingsKey("test.resolve.bool", default = false)
    val stringKey = SettingsKey("test.resolve.string", default = "def")
    val longKey = SettingsKey("test.resolve.long", default = 7L)
    val doubleKey = SettingsKey("test.resolve.double", default = 0.5)

    assertEquals(7, intKey[context], "unset int falls back to its default")
    assertEquals(false, boolKey[context], "unset bool falls back to its default")
    assertEquals("def", stringKey[context], "unset string falls back to its default")
    assertEquals(7L, longKey[context], "unset long falls back to its default")
    assertEquals(0.5, doubleKey[context], "unset double falls back to its default")

    intKey[context] = 42
    boolKey[context] = true
    stringKey[context] = "hello"
    longKey[context] = Long.MAX_VALUE
    doubleKey[context] = 2.25

    assertEquals(42, intKey[context])
    assertEquals(true, boolKey[context])
    assertEquals("hello", stringKey[context])
    assertEquals(Long.MAX_VALUE, longKey[context])
    assertEquals(2.25, doubleKey[context])
  }

  @Test
  fun `a read-only key resolves its default even when a layer holds a value for it`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    val readOnlyKey = SettingsKey("test.resolve.readonly", default = "def", readonly = true)
    val fence = SettingsKey("test.resolve.readonlyFence", default = "def")

    provider.put(context, readOnlyKey.identity, SettingsValue.StringValue("stored"))
    // The vault takes the pushes in order, so once the fence reads, the stored value is in the model.
    provider.seed(context, fence, "fenced")
    awaitEquals("fenced") { fence[context] }

    assertEquals("def", readOnlyKey[context])
    assertTrue(readOnlyKey.values(context).isEmpty())
  }
}
