// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Pins the multidimensional (slotted) resolution. A child key stored in a [SettingsMapKey] resolves through the slot
 * that the discriminator of the map key selects. A value pinned on the context with [access] selects the slot, or else
 * the resolved value of the discriminator key does. An unknown slot falls back to the default of the child key.
 */
@TestApplication
class SettingsMultiDimTest {
  companion object {
    private val currentProfile = SettingsKey("test.md.profile", default = "default")
    private val profileData = SettingsMapKey("test.md.profileData", discriminatorKey = currentProfile)
    private val inspection = SettingsKey("test.md.inspection", default = "inspectionDefault", storageKey = profileData)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  /** Fills the "default" and "release" profile slots by writing the child against each pinned slot. */
  private suspend fun writeSlots(context: SettingsContext) {
    inspection[context.access { this[currentProfile] = "default" }] = "defaultProfileValue"
    inspection[context.access { this[currentProfile] = "release" }] = "releaseProfileValue"
  }

  @Test
  fun `a child resolves through the slot pinned on the context, else the child default`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()
    writeSlots(context)

    awaitEquals("defaultProfileValue") { inspection[context] } // discriminator's default value → "default" slot
    awaitEquals("releaseProfileValue") { inspection[context.access { this[currentProfile] = "release" }] } // pinned slot
    awaitEquals("inspectionDefault") { inspection[context.access { this[currentProfile] = "absent" }] } // no such slot → default
  }

  @Test
  fun `a child follows the discriminator value carried in the data, without pinning`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()
    writeSlots(context)

    currentProfile[context] = "release"
    awaitEquals("releaseProfileValue") { inspection[context] } // discriminator resolved from the data selects "release"

    currentProfile[context] = "default"
    awaitEquals("defaultProfileValue") { inspection[context] } // changing it in the data reselects the slot
  }
}
