// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Pins the two-level slotted resolution. Each slot of a `language` map key holds a nested `profile` map key (through
 * [SettingsKey.storageKey]), and each slot of that one holds the child. A child resolves through the language, then
 * the profile. A missing slot at either level falls back to the default of the child.
 */
@TestApplication
class SettingsNestedDimTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  private val languageKey = SettingsKey("test.2d.language", default = "any")
  private val profileKey = SettingsKey("test.2d.profile", default = "default")
  private val languageData = SettingsMapKey("test.2d.languageData", discriminatorKey = languageKey)
  private val profileData = SettingsMapKey("test.2d.profileData", discriminatorKey = profileKey, storageKey = languageData)
  private val inspection = SettingsKey("test.2d.inspection", default = "inspectionDefault", storageKey = profileData)

  @Test
  fun `a child resolves through language then profile, missing slots fall back to the default`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    // Fill the language × profile cells by writing the child against each pinned (language, profile) slot.
    inspection[context.access { this[languageKey] = "kotlin"; this[profileKey] = "default" }] = "kotlinDefault"
    inspection[context.access { this[languageKey] = "kotlin"; this[profileKey] = "release" }] = "kotlinRelease"
    inspection[context.access { this[languageKey] = "java"; this[profileKey] = "default" }] = "javaDefault"

    awaitEquals("kotlinRelease") { inspection[context.access { this[languageKey] = "kotlin"; this[profileKey] = "release" }] }
    awaitEquals("kotlinDefault") { inspection[context.access { this[languageKey] = "kotlin"; this[profileKey] = "default" }] }
    awaitEquals("javaDefault") { inspection[context.access { this[languageKey] = "java"; this[profileKey] = "default" }] }
    // java has no "release" profile slot → child default
    awaitEquals("inspectionDefault") { inspection[context.access { this[languageKey] = "java"; this[profileKey] = "release" }] }
    // no "python" language slot at all → child default
    awaitEquals("inspectionDefault") { inspection[context.access { this[languageKey] = "python"; this[profileKey] = "default" }] }
  }
}
