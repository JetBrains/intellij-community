// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import javax.swing.text.JTextComponent

@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
@RegistryKey(key = "platform.disable.plugin.update.sources.ui.and.filtering.for.internal.users", value = "false")
@RegistryKey(key = "platform.make.plugin.update.source.visible.in.ui", value = "true")
@RegistryKey(key = "platform.limit.plugin.update.source.by.configured.one", value = "true")
@TestApplication
internal class PluginUnknownUpdateSourceWarningTest {
  private val pluginId = PluginId.getId("com.intellij")

  private data class UnknownUpdateSourceWarningCase(
    val name: String,
    val isBundled: Boolean,
    val allowBundledUpdate: Boolean,
    val isVisible: Boolean,
  )

  @Test
  fun `unknown update source warning is shown only when the plugin can be updated`(): Unit = timeoutRunBlocking {
    val softly = SoftAssertions()
    withHost { host ->
      for (testCase in unknownUpdateSourceWarningCases()) {
        UiPluginManager.getInstance().setPendingPluginUpdateSourceInSession(host.sessionId, pluginId, null)
        val plugin = plugin(testCase)
        withContext(Dispatchers.EDT) {
          val details = host.createDetails(LinkListener<Any> { _, _ -> }, marketplace = false)
          try {
            details.showPluginImpl(plugin, updateDescriptor = null)
            softly.assertThat(details.findUnknownUpdateSourceBanner().isVisible)
              .describedAs("${testCase.name}, details")
              .isEqualTo(testCase.isVisible)

            val row = createRow(host, plugin)
            try {
              softly.assertThat(row.findUnknownUpdateSourceWarning().isVisible)
                .describedAs("${testCase.name}, list row")
                .isEqualTo(testCase.isVisible)
            }
            finally {
              host.releaseRow(row)
            }
          }
          finally {
            host.releaseDetails(details)
          }
        }
      }
    }
    softly.assertAll()
  }

  private suspend fun CoroutineScope.withHost(action: suspend (LegacyPluginUiHost) -> Unit) {
    val host = withContext(Dispatchers.EDT) {
      LafManager.getInstance()
      LegacyPluginUiHost(
        parentScope = this@withHost,
        operationScope = this@withHost,
        unifiedDetailsPageLayout = true,
      )
    }
    try {
      host.awaitSessionInitialization()
      action(host)
    }
    finally {
      withContext(Dispatchers.EDT) {
        host.dispose(closeSession = true)
      }
    }
  }

  private fun createRow(host: LegacyPluginUiHost, plugin: PluginUiModel): ListPluginComponent {
    val group = PluginsGroup("Installed", PluginsGroupType.INSTALLED)
    group.addModel(plugin)
    val listModel = ListPluginModel().apply {
      installedModels[pluginId] = plugin
      setPluginInstallationState(pluginId, PluginInstallationState(fullyInstalled = true))
    }
    return host.createRow(plugin, group, listModel, LinkListener<Any> { _, _ -> }, marketplace = false)
  }

  private fun unknownUpdateSourceWarningCases(): List<UnknownUpdateSourceWarningCase> {
    return listOf(
      UnknownUpdateSourceWarningCase(
        name = "not bundled",
        isBundled = false,
        allowBundledUpdate = false,
        isVisible = true,
      ),
      UnknownUpdateSourceWarningCase(
        name = "bundled and allowed to be updated",
        isBundled = true,
        allowBundledUpdate = true,
        isVisible = true,
      ),
      UnknownUpdateSourceWarningCase(
        name = "bundled and not allowed to be updated",
        isBundled = true,
        allowBundledUpdate = false,
        isVisible = false,
      ),
    )
  }

  private fun plugin(testCase: UnknownUpdateSourceWarningCase): PluginUiModel {
    val plugin = PluginDto("Plugin", pluginId).apply {
      version = "1.0"
      description = "Some plugin"
      detailsLoaded = true
      isBundled = testCase.isBundled
      allowBundledUpdate = testCase.allowBundledUpdate
    }
    assertThat(plugin.isUpdateable).isEqualTo(!testCase.isBundled || testCase.allowBundledUpdate)
    return plugin
  }

  private fun PluginDetailsPageComponent.findUnknownUpdateSourceBanner(): UpdateSourceBanner {
    return UIUtil.uiTraverser(this)
      .filter(UpdateSourceBanner::class.java)
      .single { it.status == EditorNotificationPanel.Status.Warning }
  }

  private fun ListPluginComponent.findUnknownUpdateSourceWarning(): JTextComponent {
    val text = IdeBundle.message("plugins.configurable.plugin.list.unknown.update.source.warning")
    return UIUtil.uiTraverser(this).filter(JTextComponent::class.java).single { it.text == text }
  }
}
