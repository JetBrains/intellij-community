// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the fault isolation of the slotted storage in a hand-edited config. A malformed [SettingsMapKey] structure
 * must not break the resolution of a child key. The faults are a map key storage that is not a map, a slot that is not
 * a nested map, and a wrongly typed child value in a valid slot. Each fault makes only the affected child fall back to
 * its default. Its well-typed siblings and slots resolve normally.
 *
 * The layers come from committed JSON that the production codec parses.
 */
@TestApplication
class SettingsMalformedMapKeyTest {
  companion object {
    private val marker = SettingsKey("test.mm.marker", default = "none")
    private val profile = SettingsKey("test.mm.profile", default = "default")
    private val profileData = SettingsMapKey("test.mm.profileData", discriminatorKey = profile)
    private val name = SettingsKey("test.mm.name", default = "nameDef", storageKey = profileData)
    private val count = SettingsKey("test.mm.count", default = -1, storageKey = profileData)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a map-key storage that is not a map leaves children at their defaults`(): Unit = runBlocking {
    seed("malformedMapStorage.json")
    val app = SettingsContext.app()

    awaitEquals("landed") { marker[app] }  // the layer has landed
    assertEquals("nameDef", name[app], "scalar stored for the whole slotted storage → child default")
    assertEquals(-1, count[app])
  }

  @Test
  fun `a slot that is not a map defaults, while a well-formed sibling slot resolves`(): Unit = runBlocking {
    seed("malformedSlot.json")
    val app = SettingsContext.app()

    awaitEquals("landed") { marker[app] }
    assertEquals("nameDef", name[app.access { this[profile] = "bad" }], "scalar stored for a slot → child default")
    assertEquals("ok", name[app.access { this[profile] = "good" }])
    assertEquals(5, count[app.access { this[profile] = "good" }])
  }

  @Test
  fun `a wrong-typed child in a valid slot defaults, while its sibling child resolves`(): Unit = runBlocking {
    seed("malformedChildInSlot.json")
    val app = SettingsContext.app()

    awaitEquals("landed") { marker[app] }  // the default of the discriminator selects the "default" slot
    assertEquals("good", name[app], "well-typed child in the slot resolves")
    assertEquals(-1, count[app], "string stored for an int child → child default")
  }

  private fun seed(file: String) {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    provider.seed(SettingsContext.app(), decode(file))
  }

  private fun decode(name: String): MapValue =
    JsonSettingsCodec.decode(Files.readString(Path.of(testResourcePath("jsonRobustness/$name"))))
}
