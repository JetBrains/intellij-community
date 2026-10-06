// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.labels.LinkListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Container

@TestApplication
internal class PluginUpdateModeButtonTest {

  @Test
  fun `unified installed details show the update mode action`(): Unit = timeoutRunBlocking {
    val details = withContext(Dispatchers.EDT) {
      createDetails(coroutineScope = this@timeoutRunBlocking)
    }
    try {
      val plugin = installedPlugin()

      withContext(Dispatchers.EDT) {
        details.showPluginImpl(plugin, updateDescriptor = null)

        val button = details.findUpdateModeButton()
        assertThat(button).isNotNull
        assertThat(button!!.isVisible).isTrue()
        assertThat(button.accessibleContext.accessibleName)
          .isEqualTo(IdeBundle.message("plugins.configurable.update.mode.action"))
        assertThat(button.toolTipText).isEqualTo(IdeBundle.message("plugins.configurable.update.mode.action"))
      }
    }
    finally {
      details.detachOnEdt()
    }
  }

  @Test
  fun `marketplace details hide the update mode action for a plugin that is not installed`(): Unit = timeoutRunBlocking {
    val details = withContext(Dispatchers.EDT) {
      createDetails(coroutineScope = this@timeoutRunBlocking, isMarketplace = true)
    }
    try {
      val plugin = marketplacePlugin()

      withContext(Dispatchers.EDT) {
        details.showPluginImpl(plugin, updateDescriptor = null)

        val button = details.findUpdateModeButton()
        assertThat(button).isNotNull
        assertThat(button!!.isVisible).isFalse()
      }
    }
    finally {
      details.detachOnEdt()
    }
  }

  @Test
  fun `unified details hide the update mode action for a plugin that is pending uninstall`(): Unit = timeoutRunBlocking {
    val details = withContext(Dispatchers.EDT) {
      createDetails(coroutineScope = this@timeoutRunBlocking)
    }
    val plugin = installedPlugin("com.intellij.modules.jna")
    val wasDeleted = plugin.isDeleted
    try {
      withContext(Dispatchers.EDT) {
        plugin.isDeleted = true
        details.showPluginImpl(plugin, updateDescriptor = null)

        val button = details.findUpdateModeButton()
        assertThat(button).isNotNull
        assertThat(button!!.isVisible).isFalse()
      }
    }
    finally {
      plugin.isDeleted = wasDeleted
      details.detachOnEdt()
    }
  }

  @Test
  fun `update mode popup offers auto update only when automatic updates are enabled`() {
    assertThat(getPluginUpdateModePopupItems(isPluginAutoUpdateEnabled = true)).containsExactly(
      PluginUpdateModePopupItem.AUTO_UPDATE,
      PluginUpdateModePopupItem.MANUAL_UPDATE,
      PluginUpdateModePopupItem.IGNORE_UPDATES,
    )
    assertThat(getPluginUpdateModePopupItems(isPluginAutoUpdateEnabled = false)).containsExactly(
      PluginUpdateModePopupItem.MANUAL_UPDATE,
      PluginUpdateModePopupItem.IGNORE_UPDATES,
    )
  }

  private fun createDetails(
    coroutineScope: CoroutineScope,
    isMarketplace: Boolean = false,
    autoUpdateEnabled: Boolean = true,
  ): PluginDetailsPageComponent {
    LafManager.getInstance()
    val model = MyPluginModel(null).apply {
      this.coroutineScope = coroutineScope
      setTopController(Configurable.TopComponentController.EMPTY)
    }
    return PluginDetailsPageComponent(
      pluginModel = PluginModelFacade(model),
      searchListener = LinkListener { _, _ -> },
      isMarketplace = isMarketplace,
      customizationStrategy = DefaultPluginDetailsPageCustomizationStrategy,
      operationLauncher = PluginOperationLauncher(coroutineScope),
      secondaryButtons = true,
      badgeTags = true,
      layout = PluginDetailsPageLayout.Unified,
      pluginsAutoUpdateEnabledProvider = { autoUpdateEnabled },
    ).also(model::addDetailPanel)
  }

  private fun installedPlugin(id: String = "com.intellij"): PluginUiModel {
    val pluginId = PluginId.getId(id)
    return PluginUiModelAdapter(checkNotNull(PluginManagerCore.getPlugin(pluginId)))
  }

  private fun marketplacePlugin(): PluginUiModel {
    val node = PluginNode(PluginId.getId("not.installed.plugin"), "Plugin", "0")
    node.version = "1.0"
    node.description = "Plugin description"
    return PluginUiModelAdapter(node)
  }

  private suspend fun PluginDetailsPageComponent.detachOnEdt() {
    withContext(NonCancellable + Dispatchers.EDT) {
      detach()
    }
  }

  private fun Container.findUpdateModeButton(): PluginUpdateModeButton? {
    for (component in components) {
      if (component is PluginUpdateModeButton) {
        return component
      }
      if (component is Container) {
        component.findUpdateModeButton()?.let {
          return it
        }
      }
    }
    return null
  }

}