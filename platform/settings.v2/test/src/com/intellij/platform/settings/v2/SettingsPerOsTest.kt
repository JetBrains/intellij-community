// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.SettingsKeyRegistry
import com.intellij.platform.settings.v2.impl.withRegistryChecks
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Checks the read-only `OS` key and the per-OS storage `PER_OS_SETTINGS` of the platform. A test layer holds the
 * slots, so a test can seed the slot of another OS.
 */
@TestApplication
class SettingsPerOsTest {
  companion object {
    private val OWNER = PluginId.getId("test.perOs.owner")

    private val platformChild = SettingsKey("test.perOs.platform.child", default = "d", storageKey = PER_OS_SETTINGS)
    private val pluginChild = SettingsKey("test.perOs.plugin.child", default = "d", storageKey = PER_OS_SETTINGS).withTestPluginId(OWNER)

    /** Holds an OS id that is not the current one. */
    private val otherOs: String get() = if (OS.getValue() == "linux") "windows" else "linux"
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  private val appContext = SettingsContext.app()

  @BeforeEach
  fun installTestLayer() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
  }

  @Test
  fun `the OS key returns the id of the current OS and refuses a write`() {
    val expected = when {
      SystemInfoRt.isWindows -> "windows"
      SystemInfoRt.isMac -> "macos"
      SystemInfoRt.isLinux -> "linux"
      SystemInfoRt.isFreeBSD -> "freebsd"
      else -> null
    }
    if (expected != null) assertEquals(expected, OS.getValue())

    assertThrows<ReadOnlySettingsException> { runBlocking { OS[appContext] = otherOs } }
  }

  @Test
  fun `a child key reads and writes the slot of the current OS`(): Unit = runBlocking {
    platformChild[appContext.accessFor(platformChild)] = "written"

    assertEquals("written", platformChild[appContext])
    assertEquals(StringValue("written"), storedSlot(OS.getValue())?.get(platformChild.identity))
    assertEquals("d", platformChild[appContext.access { this[OS] = otherOs }])
  }

  @Test
  fun `a value in the slot of another OS is not read, and a read pinned to that OS reads it`(): Unit = runBlocking {
    val otherOsPinned = appContext.access { this[OS] = otherOs }
    platformChild[otherOsPinned] = "of the other OS"

    assertEquals("d", platformChild[appContext])
    assertEquals("of the other OS", platformChild[otherOsPinned])
  }

  @Test
  fun `a child key of a plugin is written to and read from the storage of the platform`(): Unit = runBlocking {
    SettingsKeyRegistry.getInstance().register(pluginChild, OWNER)
    SettingsKeyRegistry.getInstance().register(PER_OS_SETTINGS, PluginManagerCore.CORE_ID)

    withRegistryChecks {
      pluginChild[appContext.accessFor(pluginChild)] = "written"
    }

    assertEquals("written", pluginChild[appContext])
    assertEquals(StringValue("written"), storedSlot(OS.getValue())?.get(pluginChild.identity))
    assertEquals("${OWNER.idString}.test.perOs.plugin.child", pluginChild.identity)
  }

  /** Writes the pending saves and returns the stored [os] slot of `PER_OS_SETTINGS` in the local layer of the application. */
  private suspend fun storedSlot(os: String): MapValue? {
    SettingsVault.getInstance().flush(appContext)
    val slots = provider.saved(appContext, LayerLocality.LOCAL)?.get(PER_OS_SETTINGS.identity) as? MapValue
    return slots?.get(os) as? MapValue
  }
}
