// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginRowRenderKeyTest {
  @Test
  fun `missing installation state follows installed counterpart presence`() {
    val plugin = pluginModel("Plugin", "1.0")
    val listModel = ListPluginModel()

    assertThat(listModel.getPluginInstallationState(plugin.pluginId)).isEqualTo(PluginInstallationState(false))

    listModel.setInstalledPlugins(mapOf(plugin.pluginId to plugin))
    assertThat(listModel.getPluginInstallationState(plugin.pluginId)).isEqualTo(PluginInstallationState(true))

    val explicitState = PluginInstallationState(false, PluginStatus.UNINSTALLED_WITHOUT_RESTART)
    listModel.setPluginInstallationState(plugin.pluginId, explicitState)
    assertThat(listModel.getPluginInstallationState(plugin.pluginId)).isEqualTo(explicitState)
  }

  @Test
  fun `installation state changes reuse a compatible row`() {
    val plugin = pluginModel("Plugin", "1.0")

    val before = renderKey(plugin, PluginInstallationState(false))
    val after = renderKey(plugin, PluginInstallationState(true, PluginStatus.UPDATED))

    assertThat(after).isEqualTo(before)
  }

  @Test
  fun `constructor presentation changes require row replacement`() {
    val before = renderKey(pluginModel("Old name", "1.0"), PluginInstallationState(false))
    val after = renderKey(pluginModel("New name", "2.0"), PluginInstallationState(false))

    assertThat(after).isNotEqualTo(before)
  }

  @Test
  fun `marketplace installed counterpart version requires row replacement`() {
    val marketplacePlugin = pluginModel("Plugin", "2.0")
    val before = renderKey(
      plugin = marketplacePlugin,
      installationState = PluginInstallationState(true),
      installedPlugin = pluginModel("Plugin", "1.0"),
      marketplace = true,
    )
    val after = renderKey(
      plugin = marketplacePlugin,
      installationState = PluginInstallationState(true),
      installedPlugin = pluginModel("Plugin", "1.1"),
      marketplace = true,
    )

    assertThat(after).isNotEqualTo(before)
  }

  @Test
  fun `marketplace installed counterpart presence requires row replacement`() {
    val marketplacePlugin = pluginModel("Plugin", "2.0")
    val before = renderKey(
      plugin = marketplacePlugin,
      installationState = PluginInstallationState(false),
      marketplace = true,
    )
    val installedPluginWithoutVersion = pluginModel("Plugin", "")
    val after = renderKey(
      plugin = marketplacePlugin,
      installationState = PluginInstallationState(true),
      installedPlugin = installedPluginWithoutVersion,
      marketplace = true,
    )

    assertThat(before.version).isNull()
    assertThat(after.version).isNull()
    assertThat(after).isNotEqualTo(before)
  }

  private fun renderKey(
    plugin: PluginUiModel,
    installationState: PluginInstallationState,
    installedPlugin: PluginUiModel? = null,
    marketplace: Boolean = false,
  ): PluginRowRenderKey {
    return ListPluginComponent.createRenderKey(
      plugin = plugin,
      installedPlugin = installedPlugin,
      installationState = installationState,
      groupType = PluginsGroupType.INSTALLED,
      marketplace = marketplace,
      pluginEnabled = true,
      restrictedByProduct = false,
      listCustomizerClassName = "test.ListCustomizer",
      pluginManagerCustomizerClassName = null,
    )
  }

  private fun pluginModel(name: String, version: String): PluginUiModel {
    val node = PluginNode(PluginId.getId("plugin.id"), name, "0")
    node.version = version
    node.vendor = "Vendor"
    node.tags = listOf("Tag")
    return PluginUiModelAdapter(node)
  }
}
