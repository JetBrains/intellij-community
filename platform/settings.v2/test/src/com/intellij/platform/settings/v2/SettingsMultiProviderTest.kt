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
 * Pins the resolution across several layer providers stacked in one context. The higher-ordered provider shadows the
 * lower one for a shared key, and a key that only the lower provider defines still resolves from it. A write goes to
 * the provider that wins on read.
 */
@TestApplication
class SettingsMultiProviderTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val lower = TestLayerProvider()
  private val higher = TestLayerProvider()

  @Test
  fun `a higher-ordered provider shadows a lower one, values only in the lower still resolve`(): Unit = runBlocking {
    // Last in the list is the highest-priority layer (the vault reverses provider order).
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)
    val context = SettingsContext.app()

    val shadowed = SettingsKey("test.mp.shadowed", default = "shadowedDefault")
    val onlyLower = SettingsKey("test.mp.onlyLower", default = "onlyLowerDefault")

    lower.seed(context, shadowed, "lower")
    higher.seed(context, shadowed, "higher")
    lower.seed(context, onlyLower, "lower")

    awaitEquals("higher") { shadowed[context] }  // both define it → the higher-ordered provider wins
    awaitEquals("lower") { onlyLower[context] }   // absent from higher → resolved from the lower provider
  }

  @Test
  fun `a write lands in the narrowest accepting provider, so the read reflects it`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)
    val context = SettingsContext.app()
    val key = SettingsKey("test.mp.writeTarget", default = "def")

    // Both providers hold the key, so the narrowest (higher) wins on read.
    lower.seed(context, key, "lower")
    higher.seed(context, key, "higher")
    awaitEquals("higher") { key[context] }

    // The write must go to that same narrowest layer, not to the shadowed lower one. Otherwise the write lands under
    // a layer that the read never returns, and it seems to have no effect.
    key[context] = "written"
    assertEquals("written", key[context])
  }
}
