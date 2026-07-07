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
 * Covers a write of a slotted key, which goes into the slots its context pins. The tests move a discriminator
 * after the pin, and write through a chain that is pinned in part or not at all.
 */
@TestApplication
class SettingsPinnedSlotWriteTest {
  companion object {
    private val profileCurrent = SettingsKey("test.pin.profile.current", default = "default")
    private val profileData = SettingsMapKey("test.pin.profile.data", discriminatorKey = profileCurrent)
    private val inspection = SettingsKey("test.pin.inspection", default = "off", storageKey = profileData)

    private val langCurrent = SettingsKey("test.pin.lang.current", default = "any")
    private val langData = SettingsMapKey("test.pin.lang.data", discriminatorKey = langCurrent, storageKey = profileData)
    private val style = SettingsKey("test.pin.style", default = "none", storageKey = langData)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `a write lands in the pinned slot after the discriminator moved`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()
    val pinned = context.accessFor(inspection)  // the "default" slot, as selected now

    profileCurrent[context] = "release"
    assertEquals("release", profileCurrent[context])

    inspection[pinned] = "on"

    assertEquals("on", inspection[context.access { this[profileCurrent] = "default" }])  // the pinned slot
    assertEquals("off", inspection[context])                                             // the current one is untouched
  }

  @Test
  fun `accessFor pins a whole chain, so both discriminators may move afterwards`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()
    val pinned = context.accessFor(style)  // the (default, any) cell

    profileCurrent[context] = "release"
    langCurrent[context] = "kotlin"

    style[pinned] = "styled"

    assertEquals("styled", style[context.access { this[profileCurrent] = "default"; this[langCurrent] = "any" }])
    assertEquals("none", style[context])  // the (release, kotlin) cell was never written
  }

  @Test
  fun `a write with no pinned slot is refused`() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    val failure = assertThrows<UnpinnedDiscriminatorException> { runBlocking { inspection[context] = "on" } }

    assertEquals(inspection, failure.key)
    assertEquals(profileData, failure.mapKey)
    assertEquals("off", inspection[context.accessFor(inspection)])  // nothing was written
  }

  @Test
  fun `a write with only the inner slot pinned is refused`() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    // Every link of the chain must be pinned. A difference at the outer one moves the whole subtree below it.
    val failure = assertThrows<UnpinnedDiscriminatorException> {
      runBlocking { style[context.access { this[langCurrent] = "kotlin" }] = "x" }
    }

    assertEquals(profileData, failure.mapKey)
  }

  @Test
  fun `a plain key needs no slot`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    profileCurrent[context] = "release"

    assertEquals("release", profileCurrent[context])
    assertEquals(context, context.accessFor(profileCurrent))  // no chain to pin
  }
}
