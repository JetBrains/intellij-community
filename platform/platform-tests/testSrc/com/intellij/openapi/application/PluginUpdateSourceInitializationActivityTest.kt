// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSource
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceInitializationActivity
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceInitializer
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.SystemProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertFalse

@TestApplication
@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
@RegistryKey(key = "update.source.initialization.enabled", value = "true")
internal class PluginUpdateSourceInitializationActivityTest : UpdateCheckerTestBase() {
  val project = projectFixture()

  private var originalCustomRepositoryHosts: List<String>? = null

  @AfterEach
  fun cleanUp() {
    originalCustomRepositoryHosts?.let { restoreCustomRepositoryHosts(it) }
    originalCustomRepositoryHosts = null

    PluginUpdateSourceInitializer.allowInitializationHappenAgain()
    eraseAllPluginUpdateSources(TESTED_PLUGIN_IDS)
  }

  @Test
  fun `plugin update sources are initialized only for unambiguous plugins and only once for non-nightly servers`(): Unit =
    timeoutRunBlocking {
      val customServer = createTestServer()
      val anotherCustomServer = createTestServer()

      setCustomRepositoryHosts(listOf(customServer.url, anotherCustomServer.url))
      setInstalledPluginMocks(*INSTALLED_PLUGINS.toTypedArray())

      setServerPlugins(
        plugins = listOf(
          RepositoryPluginMock(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "501", "101", "2.0"),
          RepositoryPluginMock(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "502", "102", "2.0"),
          RepositoryPluginMock(UNKNOWN_PLUGIN, "503", "103", "2.0"),
          RepositoryPluginMock(FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN, "504", "104", "2.0"),
        ),
        updates = emptyList(),
      )

      val customRepoPlugins = INSTALLED_PLUGINS
        .filter { it.id != FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN }
        .map { CustomRepositoryPlugin(it.id, "9.0") }
      setCustomRepositoryPlugins(customServer, customRepoPlugins)
      setCustomRepositoryPlugins(anotherCustomServer, listOf(CustomRepositoryPlugin(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN, "10.0")))

      val pluginUpdateSourceService = PluginUpdateSourceService.getInstance()
      val marketplaceUpdateSourceId = pluginUpdateSourceService.createMarketplacePluginUpdateSourceId()
      pluginUpdateSourceService.setPluginUpdateSourceId(pluginId(ALREADY_INITIALIZED_PLUGIN), marketplaceUpdateSourceId)

      executeInitializationActivity()

      val customRepositoryUpdateSourceId = pluginUpdateSourceService.createCustomRepositoryPluginUpdateSourceId(customServer.url)
      assertPluginUpdateSource(ALREADY_INITIALIZED_PLUGIN, marketplaceUpdateSourceId)
      assertNoPluginUpdateSource(UNKNOWN_PLUGIN)
      assertNoPluginUpdateSource(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN)
      assertPluginUpdateSource(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
      assertPluginUpdateSource(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
      assertPluginUpdateSource(FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN, marketplaceUpdateSourceId)

      assertPluginUpdateSource(BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
      assertNoPluginUpdateSource(BUNDLED_NON_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN)
      assertPluginUpdateSource(BUNDLED_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
      assertNoPluginUpdateSource(BUNDLED_NON_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN)

      val sourcesAfterFirstInitialization = pluginUpdateSourcesByPluginId()
      executeInitializationActivity()
      assertEquals(sourcesAfterFirstInitialization, pluginUpdateSourcesByPluginId())

      eraseAllPluginUpdateSources(TESTED_PLUGIN_IDS)
      executeInitializationActivity()
      for (pluginId in TESTED_PLUGIN_IDS) {
        assertNoPluginUpdateSource(pluginId)
      }
    }

  @Test
  fun `plugin update sources are initialized only for unambiguous plugins and only once with nightly servers`(): Unit = timeoutRunBlocking {
    val customServer = createTestServer()
    val firstNightlyServer = createTestServer()
    val secondNightlyServer = createTestServer()

    setInstalledPluginMocks(*INSTALLED_PLUGINS.toTypedArray())
    setCustomRepositoryHosts(listOf(customServer.url))
    withSystemProperty(CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY, "${firstNightlyServer.url},${secondNightlyServer.url}") {
      setCustomRepositoryPlugins(customServer, listOf(CustomRepositoryPlugin(NIGHTLY_AND_CUSTOM_REPOSITORIES_PLUGIN, "9.0")))
      setCustomRepositoryPlugins(firstNightlyServer,
                                 listOf(
                                   CustomRepositoryPlugin(SINGLE_NIGHTLY_REPOSITORY_PLUGIN, "11.0"),
                                   CustomRepositoryPlugin(BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_NIGHTLY_REPOSITORY_PLUGIN, "14.0"),
                                   CustomRepositoryPlugin(MULTIPLE_NIGHTLY_REPOSITORIES_PLUGIN, "12.0"),
                                   CustomRepositoryPlugin(NIGHTLY_AND_CUSTOM_REPOSITORIES_PLUGIN, "15.0")
                                 ))
      setCustomRepositoryPlugins(secondNightlyServer,
                                 listOf(
                                   CustomRepositoryPlugin(MULTIPLE_NIGHTLY_REPOSITORIES_PLUGIN, "13.0"),
                                 ))

      val pluginUpdateSourceService = PluginUpdateSourceService.getInstance()
      val marketplaceUpdateSourceId = pluginUpdateSourceService.createMarketplacePluginUpdateSourceId()
      pluginUpdateSourceService.setPluginUpdateSourceId(pluginId(ALREADY_INITIALIZED_PLUGIN), marketplaceUpdateSourceId)

      executeInitializationActivity()

      val nightlyRepositoryUpdateSourceId = pluginUpdateSourceService.createCustomRepositoryPluginUpdateSourceId(firstNightlyServer.url)
      PluginUpdateSourceService.getInstance().getPluginUpdateSourceId(PluginId(SINGLE_NIGHTLY_REPOSITORY_PLUGIN)).run {
        assertNotNull(this)
        assertTrue(this.canInstallUpdatesFrom(nightlyRepositoryUpdateSourceId))
        assertFalse(this.canInstallUpdatesFrom(marketplaceUpdateSourceId))
      }
      PluginUpdateSourceService.getInstance()
        .getPluginUpdateSourceId(PluginId(BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_NIGHTLY_REPOSITORY_PLUGIN)).run {
          assertNotNull(this)
          assertTrue(this.canInstallUpdatesFrom(nightlyRepositoryUpdateSourceId))
          assertTrue(this.canInstallUpdatesFrom(marketplaceUpdateSourceId))
        }
      assertPluginUpdateSource(MULTIPLE_NIGHTLY_REPOSITORIES_PLUGIN, nightlyRepositoryUpdateSourceId)
      assertNoPluginUpdateSource(NIGHTLY_AND_CUSTOM_REPOSITORIES_PLUGIN)

      val sourcesAfterFirstInitialization = pluginUpdateSourcesByPluginId()
      executeInitializationActivity()
      assertEquals(sourcesAfterFirstInitialization, pluginUpdateSourcesByPluginId())

      eraseAllPluginUpdateSources(TESTED_PLUGIN_IDS)
      executeInitializationActivity()
      for (pluginId in TESTED_PLUGIN_IDS) {
        assertNoPluginUpdateSource(pluginId)
      }
    }
  }

  private fun setCustomRepositoryHosts(hosts: List<String>) {
    originalCustomRepositoryHosts = originalCustomRepositoryHosts ?: UpdateSettings.getInstance().storedPluginHosts.toList()
    restoreCustomRepositoryHosts(hosts)
  }

  private fun restoreCustomRepositoryHosts(hosts: List<String>) {
    UpdateSettings.getInstance().state.pluginHosts.apply {
      clear()
      addAll(hosts)
    }
  }

  private suspend fun withSystemProperty(key: String, value: String?, task: suspend () -> Unit) {
    val original = if (value != null) System.setProperty(key, value) else System.clearProperty(key)
    try {
      task.invoke()
    }
    finally {
      SystemProperties.setProperty(key, original)
    }
  }

  private fun pluginUpdateSourcesByPluginId(): Map<String, PluginUpdateSource?> {
    return TESTED_PLUGIN_IDS.associateWith { pluginId ->
      PluginUpdateSourceService.getInstance().getPersistedPluginUpdateSourceId(pluginId(pluginId))
    }
  }

  private fun assertPluginUpdateSource(pluginId: String, expectedUpdateSourceId: PluginUpdateSource) {
    assertEquals(expectedUpdateSourceId, PluginUpdateSourceService.getInstance().getPersistedPluginUpdateSourceId(pluginId(pluginId)))
  }

  private fun assertNoPluginUpdateSource(pluginId: String) {
    assertNull(PluginUpdateSourceService.getInstance().getPersistedPluginUpdateSourceId(pluginId(pluginId)))
  }

  private fun eraseAllPluginUpdateSources(pluginIds: Collection<String>) {
    val pluginUpdateSourceService = PluginUpdateSourceService.getInstance()
    for (pluginId in pluginIds) {
      pluginUpdateSourceService.erasePluginUpdateSourceId(pluginId(pluginId))
    }
  }

  private suspend fun executeInitializationActivity() {
    val activity = PluginUpdateSourceInitializationActivity()
    activity.execute(project.get())
  }

  private fun pluginId(pluginId: String): PluginId = PluginId.getId(pluginId)

  private companion object {
    const val ALREADY_INITIALIZED_PLUGIN = "test.already.initialized.update.source"
    const val UNKNOWN_PLUGIN = "test.unknown.update.source"
    const val MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN = "test.multiple.custom.repositories.update.source"
    const val FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN = "com.intellij.java"
    const val NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN = "test.single.custom.repository.update.source"
    const val FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN = "org.jetbrains.plugins.github"

    const val BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN = "test.bundled.updateable.jet.brains.in.custom.repository"
    const val BUNDLED_NON_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN =
      "test.bundled.non.updateable.jet.brains.in.custom.repository"
    const val BUNDLED_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN = "test.bundled.updateable.in.custom.repository"
    const val BUNDLED_NON_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN = "test.bundled.non.updateable.in.custom.repository"
    const val SINGLE_NIGHTLY_REPOSITORY_PLUGIN = "test.single.nightly.repository"
    const val BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_NIGHTLY_REPOSITORY_PLUGIN = "test.bundled.updateable.jet.brains.in.nightly.repository"
    const val MULTIPLE_NIGHTLY_REPOSITORIES_PLUGIN = "test.multiple.nightly.repositories"
    const val NIGHTLY_AND_CUSTOM_REPOSITORIES_PLUGIN = "test.nightly.and.custom.repositories"

    val INSTALLED_PLUGINS = listOf(
      installedPlugin(ALREADY_INITIALIZED_PLUGIN),
      installedPlugin(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN),
      installedPlugin(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN),
      installedPlugin(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN),
      installedPlugin(FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN),

      installedPlugin(BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN,
                      isBundled = true,
                      allowBundledUpdate = true,
                      isJetBrainsPlugin = true),
      installedPlugin(BUNDLED_NON_UPDATEABLE_JET_BRAINS_PLUGIN_CUSTOM_REPOSITORY_PLUGIN, isBundled = true, isJetBrainsPlugin = true),
      installedPlugin(BUNDLED_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN, isBundled = true, allowBundledUpdate = true),
      installedPlugin(BUNDLED_NON_UPDATEABLE_PLUGIN_CUSTOM_REPOSITORY_PLUGIN, isBundled = true),
      installedPlugin(SINGLE_NIGHTLY_REPOSITORY_PLUGIN, isBundled = true, allowBundledUpdate = true),
      installedPlugin(BUNDLED_UPDATEABLE_JET_BRAINS_PLUGIN_NIGHTLY_REPOSITORY_PLUGIN,
                      isBundled = true,
                      allowBundledUpdate = true,
                      isJetBrainsPlugin = true),
      installedPlugin(MULTIPLE_NIGHTLY_REPOSITORIES_PLUGIN),
      installedPlugin(NIGHTLY_AND_CUSTOM_REPOSITORIES_PLUGIN)
    )


    val TESTED_PLUGIN_IDS = buildList {
      addAll(INSTALLED_PLUGINS.map { it.id })
      add(UNKNOWN_PLUGIN)
    }

    private fun installedPlugin(
      pluginId: String,
      isBundled: Boolean = false,
      allowBundledUpdate: Boolean = false,
      isJetBrainsPlugin: Boolean = false,
    ): InstalledPluginMock {
      val vendor = if (isJetBrainsPlugin) "JetBrains" else "Some Company"
      return InstalledPluginMock(pluginId, pluginId, vendor, "1.0", "1.0", "999.9999", true, vendor, isBundled, allowBundledUpdate)
    }
  }
}
