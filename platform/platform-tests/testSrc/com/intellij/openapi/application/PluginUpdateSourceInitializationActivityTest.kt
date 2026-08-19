// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceInitializationActivity
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceInitializer
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.platform.pluginSystem.testFramework.PluginSetSpecBuilder
import com.intellij.platform.pluginSystem.testFramework.buildPluginSet
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
@RegistryKey(key = "update.source.initialization.enabled", value = "true")
internal class PluginUpdateSourceInitializationActivityTest : UpdateCheckerTestBase() {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()
  val project = projectFixture()

  private var originalPluginSet: PluginSet? = null
  private var originalCustomRepositoryHosts: List<String>? = null

  @AfterEach
  fun cleanUp() {
    originalPluginSet?.let { PluginManagerCore.setPluginSet(it) }
    originalPluginSet = null

    originalCustomRepositoryHosts?.let { restoreCustomRepositoryHosts(it) }
    originalCustomRepositoryHosts = null

    PluginUpdateSourceInitializer.allowInitializationHappenAgain()
    eraseAllPluginUpdateSources(TESTED_PLUGIN_IDS)
  }

  @Test
  fun `plugin update sources are initialized only for unambiguous plugins and only once`(): Unit = timeoutRunBlocking {
    val customServer = createTestServer(testDisposable.get())
    val anotherCustomServer = createTestServer(testDisposable.get())
    val customRepositoryUrl = customServer.url + "/custom-repository"
    val anotherCustomRepositoryUrl = anotherCustomServer.url + "/custom-repository"

    setCustomRepositoryHosts(listOf(customRepositoryUrl, anotherCustomRepositoryUrl))
    setInstalledPlugins(INSTALLED_PLUGIN_IDS)

    setServerPlugins(
      plugins = listOf(
        RepositoryPluginMock(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "501", "101", "2.0"),
        RepositoryPluginMock(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "502", "102", "2.0"),
        RepositoryPluginMock(UNKNOWN_PLUGIN, "503", "103", "2.0"),
        RepositoryPluginMock(FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN, "504", "104", "2.0"),
      ),
      updates = emptyList(),
    )
    setCustomRepositoryPlugins(customServer,
                               listOf(
                                 CustomRepositoryPlugin(ALREADY_INITIALIZED_PLUGIN, "9.0"),
                                 CustomRepositoryPlugin(UNKNOWN_PLUGIN, "9.0"),
                                 CustomRepositoryPlugin(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN, "9.0"),
                                 CustomRepositoryPlugin(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "9.0"),
                                 CustomRepositoryPlugin(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, "9.0"),
                               ))
    setCustomRepositoryPlugins(anotherCustomServer,
                               listOf(
                                 CustomRepositoryPlugin(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN, "10.0"),
                               ))

    val pluginUpdateSourceService = PluginUpdateSourceService.getInstance()
    val marketplaceUpdateSourceId = pluginUpdateSourceService.createMarketplacePluginUpdateSourceId()
    pluginUpdateSourceService.setPluginUpdateSourceId(pluginId(ALREADY_INITIALIZED_PLUGIN), marketplaceUpdateSourceId)

    executeInitializationActivity()

    val customRepositoryUpdateSourceId = pluginUpdateSourceService.createCustomRepositoryPluginUpdateSourceId(customRepositoryUrl)
    assertPluginUpdateSource(ALREADY_INITIALIZED_PLUGIN, marketplaceUpdateSourceId)
    assertNoPluginUpdateSource(UNKNOWN_PLUGIN)
    assertNoPluginUpdateSource(MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN)
    assertPluginUpdateSource(FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
    assertPluginUpdateSource(NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN, customRepositoryUpdateSourceId)
    assertPluginUpdateSource(FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN, marketplaceUpdateSourceId)

    val sourcesAfterFirstInitialization = pluginUpdateSourcesByPluginId()
    executeInitializationActivity()
    assertEquals(sourcesAfterFirstInitialization, pluginUpdateSourcesByPluginId())

    eraseAllPluginUpdateSources(TESTED_PLUGIN_IDS)
    executeInitializationActivity()
    for (pluginId in TESTED_PLUGIN_IDS) {
      assertNoPluginUpdateSource(pluginId)
    }
  }

  private fun setInstalledPlugins(pluginIds: List<String>) {
    originalPluginSet = originalPluginSet ?: PluginManagerCore.getPluginSetOrNull()

    val pluginsDirPath = inMemoryFs.fs.getPath("/").resolve("plugins")
    val pluginSet = buildPluginSet(pluginsDirPath) {
      for (pluginId in pluginIds) {
        testPlugin(pluginId)
      }
    }
    PluginManagerCore.setPluginSet(pluginSet)
  }

  private fun PluginSetSpecBuilder.testPlugin(pluginId: String) {
    plugin(pluginId) {
      version = "1.0"
      vendor = "JetBrains"
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

  private fun pluginUpdateSourcesByPluginId(): Map<String, PluginUpdateSourceId?> {
    return TESTED_PLUGIN_IDS.associateWith { pluginId ->
      PluginUpdateSourceService.getInstance().getPluginUpdateSourceId(pluginId(pluginId))
    }
  }

  private fun assertPluginUpdateSource(pluginId: String, expectedUpdateSourceId: PluginUpdateSourceId) {
    assertEquals(expectedUpdateSourceId, PluginUpdateSourceService.getInstance().getPluginUpdateSourceId(pluginId(pluginId)))
  }

  private fun assertNoPluginUpdateSource(pluginId: String) {
    assertNull(PluginUpdateSourceService.getInstance().getPluginUpdateSourceId(pluginId(pluginId)))
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

    val INSTALLED_PLUGIN_IDS = listOf(ALREADY_INITIALIZED_PLUGIN,
                                      MULTIPLE_CUSTOM_REPOSITORIES_PLUGIN,
                                      FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN,
                                      NOT_FROM_LIST_SINGLE_CUSTOM_REPOSITORY_PLUGIN,
                                      FROM_LIST_NO_CUSTOM_REPOSITORY_PLUGIN)


    val TESTED_PLUGIN_IDS = buildList {
      addAll(INSTALLED_PLUGIN_IDS)
      add(UNKNOWN_PLUGIN)
    }
  }
}
