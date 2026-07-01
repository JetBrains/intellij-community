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
 * Checks that a default factory of a slotted key finds the slot path of the read with [accessFor] (ADR 0156). A
 * plugin contributes defaults for the slots of its own scheme, and the factory looks them up by the resolved
 * discriminators. The profile discriminator is itself slotted by the scheme, so it resolves in the slot of the scheme.
 */
@TestApplication
class SettingsContributedDefaultTest {
  companion object {
    private val scheme = SettingsKey("test.contributed.scheme", default = "platform")
    private val schemeData = SettingsMapKey("test.contributed.schemeData", discriminatorKey = scheme)
    private val profile = SettingsKey("test.contributed.profile", default = "light", storageKey = schemeData)
    private val profileData = SettingsMapKey("test.contributed.profileData", discriminatorKey = profile, storageKey = schemeData)

    /** Holds the defaults that a plugin ships for the slots of its scheme, as its extension would. */
    private val contributedDefaults = mapOf(
      ("plugin1" to "dark") to "plugin1 dark",
      ("plugin1" to "light") to "plugin1 light",
      ("platform" to "light") to "platform light",
      ("platform" to "dark") to "platform dark",
    )

    private val color: SettingsKey<String> = SettingsKey(
      "test.contributed.color", StringSerializer, storageKey = profileData,
      defaultFactory = { context -> contributedDefaultOf(context.accessFor(color)) },
    )

    private fun contributedDefaultOf(slotPath: SettingsContext): String {
      val slot = slotPath.discriminator(scheme) to slotPath.discriminator(profile)
      return contributedDefaults[slot] ?: "hardcoded"
    }
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()

  private suspend fun maskProviders() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
  }

  private suspend fun selectPlugin1Dark() {
    scheme[app] = "plugin1"
    profile[app.access { this[scheme] = "plugin1" }] = "dark"
  }

  @Test
  fun `a default factory sees the slots that the layers select`(): Unit = runBlocking {
    maskProviders()
    selectPlugin1Dark()

    assertEquals("plugin1 dark", color[app])
  }

  @Test
  fun `a default factory keeps the slots that the caller pins`(): Unit = runBlocking {
    maskProviders()
    selectPlugin1Dark()

    assertEquals("plugin1 light", color[app.access { this[profile] = "light" }])
    assertEquals("platform light", color[app.access { this[scheme] = "platform" }])
  }

  @Test
  fun `an inner discriminator resolves in the slot of the default of the outer one`(): Unit = runBlocking {
    maskProviders()
    profile[app.access { this[scheme] = "platform" }] = "dark"

    assertEquals("platform dark", color[app])
  }
}
