// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Checks a direct write of a map key. Each public write path refuses it and stores nothing. A write of a child key
 * still lands in its slot.
 */
@TestApplication
class SettingsMapKeyDirectWriteTest {
  companion object {
    private val profileCurrent = SettingsKey("test.direct.profile.current", default = "default")
    private val profileData = SettingsMapKey("test.direct.profile.data", discriminatorKey = profileCurrent)
    private val inspection = SettingsKey("test.direct.inspection", default = "off", storageKey = profileData)

    private val rawSlots = MapValue(mapOf("default" to MapValue(mapOf(inspection.identity to StringValue("on")))))
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun installProvider() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
  }

  @Test
  fun `the key set operator refuses a map key`() {
    val context = SettingsContext.app()

    assertThrows<IllegalArgumentException> { runBlocking { profileData[context] = rawSlots } }

    assertNothingStored(context)
  }

  @Test
  fun `the context set operator refuses a map key`() {
    val context = SettingsContext.app()

    assertThrows<IllegalArgumentException> { runBlocking { context[profileData] = rawSlots } }

    assertNothingStored(context)
  }

  @Test
  fun `a vault write refuses a map key`() {
    val context = SettingsContext.app()

    assertThrows<IllegalArgumentException> { runBlocking { SettingsVault.getInstance().set(context, profileData, rawSlots) } }

    assertNothingStored(context)
  }

  @Test
  fun `a batch with a map key entry fails as a whole`() {
    val context = SettingsContext.app()

    assertThrows<IllegalArgumentException> {
      runBlocking {
        context.mutate()
          .set(inspection, "on", context.accessFor(inspection))
          .set(profileData, rawSlots)
          .apply()
      }
    }

    assertNothingStored(context)
  }

  @Test
  fun `a child key write lands in its slot`(): Unit = runBlocking {
    val context = SettingsContext.app()

    inspection[context.accessFor(inspection)] = "on"
    context.mutate().set(inspection, "on", context.access { this[profileCurrent] = "release" }).apply()

    assertEquals("on", inspection[context])
    assertEquals("on", inspection[context.access { this[profileCurrent] = "release" }])
  }

  private fun assertNothingStored(context: SettingsContext) {
    assertTrue(SettingsVault.getInstance().snapshot(context, profileData).none { it[profileData] != null })
    assertEquals("off", inspection[context])
  }
}
