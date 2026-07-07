// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Checks the prefix that a key carries on disk. It is the prefix that the plugin of the key declares, or else the
 * plugin id. A declared prefix lets two plugins hold one identity for one setting. A feature that is split over a
 * frontend and a backend plugin needs this.
 */
@TestApplication
class SettingsKeyPrefixTest {
  companion object {
    private val OWNER = PluginId.getId("test.prefix.owner")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a key with no plugin keeps its bare name`() {
    val key = SettingsKey("test.prefix.bare", default = "d")

    assertEquals("test.prefix.bare", key.identity)
  }

  @Test
  fun `a key of a plugin that declared nothing takes the plugin id`() {
    val key = SettingsKey("test.prefix.byId", default = "d").withTestPluginId(OWNER)

    assertEquals("test.prefix.owner.test.prefix.byId", key.identity)
  }

  @Test
  fun `a key takes the prefix its plugin declared`() {
    declarePrefix("short")
    val key = SettingsKey("test.prefix.declared", default = "d").withTestPluginId(OWNER)

    assertEquals("short.test.prefix.declared", key.identity)
  }

  @Test
  fun `a key resolves its prefix at its first use, not when it is constructed`() {
    val key = SettingsKey("test.prefix.lateDeclared", default = "d").withTestPluginId(OWNER)
    declarePrefix("late")

    assertEquals("late.test.prefix.lateDeclared", key.identity)
  }

  @Test
  fun `a key name that starts with the storage marker is rejected`() {
    assertThrows<IllegalArgumentException> { SettingsKey("${SettingsKey.STORAGE_IDENTITY_PREFIX}test.prefix.markerName", default = "d") }
  }

  @Test
  fun `a prefix declared by another plugin leaves the key alone`() {
    declarePrefix("short", PluginId.getId("test.prefix.other"))
    val key = SettingsKey("test.prefix.otherPlugin", default = "d").withTestPluginId(OWNER)

    assertEquals("test.prefix.owner.test.prefix.otherPlugin", key.identity)
  }

  @Test
  fun `a map key marks the same prefixed identity as storage`() {
    declarePrefix("short")
    val discriminator = SettingsKey("test.prefix.disc", default = "a").withTestPluginId(OWNER)
    val mapKey = SettingsMapKey("test.prefix.map", discriminatorKey = discriminator).withTestPluginId(OWNER)

    assertEquals("${SettingsKey.STORAGE_IDENTITY_PREFIX}short.test.prefix.map", mapKey.identity)
    assertEquals("short.test.prefix.disc", discriminator.identity)
  }

  @Test
  fun `a prefix that starts with the storage marker is rejected`() {
    declarePrefix("${SettingsKey.STORAGE_IDENTITY_PREFIX}bad")
    val declared = SettingsKey("test.prefix.markerDeclared", default = "d").withTestPluginId(OWNER)
    val markerPlugin = PluginId.getId("${SettingsKey.STORAGE_IDENTITY_PREFIX}bad")
    val fromPlugin = SettingsKey("test.prefix.markerPlugin", default = "d").withTestPluginId(markerPlugin)

    // The prefix resolves on first use, so the identity is where it fails.
    assertThrows<IllegalArgumentException> { declared.identity }
    assertThrows<IllegalArgumentException> { fromPlugin.identity }
  }

  private fun declarePrefix(prefix: String, pluginId: PluginId = OWNER) {
    val bean = SettingsKeyPrefixBean().apply {
      this.prefix = prefix
      setPluginDescriptor(DefaultPluginDescriptor(pluginId, null))
    }
    ExtensionTestUtil.maskExtensions(SettingsKeyPrefixBean.EP_NAME, listOf(bean), disposable)
  }
}
