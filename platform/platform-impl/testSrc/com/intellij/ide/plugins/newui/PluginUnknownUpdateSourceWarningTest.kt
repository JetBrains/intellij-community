// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.updateSettings.impl.PluginUpdateExclusionService
import com.intellij.openapi.updateSettings.impl.PluginUpdateExclusionService.Exclusion
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.text.JTextComponent

@RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
@RegistryKey(key = "platform.disable.plugin.update.sources.ui.and.filtering.for.internal.users", value = "false")
@RegistryKey(key = "platform.make.plugin.update.source.visible.in.ui", value = "true")
@RegistryKey(key = "platform.limit.plugin.update.source.by.configured.one", value = "true")
@RegistryKey(key = "platform.enable.plugin.update.mode.feature", value = "true")
@TestApplication
internal class PluginUnknownUpdateSourceWarningTest {
  private val pluginId = PluginId.getId("test.unknown.update.source.plugin")

  private data class UnknownUpdateSourceWarningCase(
    val name: String,
    val isBundled: Boolean,
    val allowBundledUpdate: Boolean,
    val exclusion: Exclusion?,
    val isVisible: Boolean,
  )

  @AfterEach
  fun resetExclusion() {
    PluginUpdateExclusionService.getInstance().setExcluded(pluginId, null)
  }

  @Test
  fun `details show the unknown update source banner only when the plugin can be updated`(): Unit = timeoutRunBlocking {
    for (testCase in unknownUpdateSourceWarningCases()) {
      withModel { model ->
        setExclusion(model, testCase.exclusion)
        val details = withContext(Dispatchers.EDT) { createDetails(model, this@timeoutRunBlocking) }
        try {
          withContext(Dispatchers.EDT) {
            details.showPluginImpl(plugin(testCase), updateDescriptor = null)
            details.updatePluginUpdateSourceUI(pluginUpdateSource = null)

            assertThat(details.findUnknownUpdateSourceBanner().isVisible).describedAs(testCase.name).isEqualTo(testCase.isVisible)
          }
        }
        finally {
          details.detachOnEdt()
        }
      }
    }
  }

  @Test
  fun `list row shows the unknown update source warning only when the plugin can be updated`(): Unit = timeoutRunBlocking {
    for (testCase in unknownUpdateSourceWarningCases()) {
      withModel { model ->
        setExclusion(model, testCase.exclusion)
        withContext(Dispatchers.EDT) {
          val row = createRow(model, this@timeoutRunBlocking, plugin(testCase))
          try {
            assertThat(row.findUnknownUpdateSourceWarning().isVisible).describedAs(testCase.name).isEqualTo(testCase.isVisible)
          }
          finally {
            row.close()
          }
        }
      }
    }
  }

  @Test
  fun `details follow the update mode for the unknown update source banner`(): Unit = timeoutRunBlocking {
    withModel { model ->
      val details = withContext(Dispatchers.EDT) { createDetails(model, this@timeoutRunBlocking) }
      try {
        withContext(Dispatchers.EDT) {
          details.showPluginImpl(plugin(), updateDescriptor = null)
          details.updatePluginUpdateSourceUI(pluginUpdateSource = null)
          val banner = details.findUnknownUpdateSourceBanner()
          assertThat(banner.isVisible).isTrue()

          changeExclusion(model, Exclusion.FromUpdate)
          assertThat(banner.isVisible).isFalse()

          changeExclusion(model, Exclusion.FromAutoUpdate)
          assertThat(banner.isVisible).isTrue()

          changeExclusion(model, Exclusion.FromUpdate)
          assertThat(banner.isVisible).isFalse()

          changeExclusion(model, null)
          assertThat(banner.isVisible).isTrue()
        }
      }
      finally {
        details.detachOnEdt()
      }
    }
  }

  @Test
  fun `list row follows the update mode for the unknown update source warning`(): Unit = timeoutRunBlocking {
    withModel { model ->
      withContext(Dispatchers.EDT) {
        val row = createRow(model, this@timeoutRunBlocking, plugin())
        try {
          val warning = row.findUnknownUpdateSourceWarning()
          assertThat(warning.isVisible).isTrue()

          changeExclusion(model, Exclusion.FromUpdate)
          assertThat(warning.isVisible).isFalse()

          changeExclusion(model, Exclusion.FromAutoUpdate)
          assertThat(warning.isVisible).isTrue()

          changeExclusion(model, Exclusion.FromUpdate)
          assertThat(warning.isVisible).isFalse()

          changeExclusion(model, null)
          assertThat(warning.isVisible).isTrue()
        }
        finally {
          row.close()
        }
      }
    }
  }

  @Test
  fun `list row keeps the warning hidden for an ignored plugin after an update source change`(): Unit = timeoutRunBlocking {
    withModel { model ->
      withContext(Dispatchers.EDT) {
        val row = createRow(model, this@timeoutRunBlocking, plugin())
        try {
          changeExclusion(model, Exclusion.FromUpdate)
          model.updateUiAfterUpdateSourceChange(pluginId, pluginUpdateSource = null)

          assertThat(row.findUnknownUpdateSourceWarning().isVisible).isFalse()
        }
        finally {
          row.close()
        }
      }
    }
  }

  @Test
  @SystemProperty(propertyKey = PlatformUtils.PLATFORM_PREFIX_KEY, propertyValue = PlatformUtils.JETBRAINS_CLIENT_PREFIX)
  fun `update mode is disabled in split frontend`() {
    assertThat(DefaultUiPluginManagerController.isPluginUpdateExclusionSupported()).isFalse()
  }

  @Test
  fun `the model session closes after normal completion`(): Unit = timeoutRunBlocking {
    val sessions = PluginManagerSessionService.getInstance()
    var sessionId: String? = null
    withModel { model ->
      sessionId = model.sessionId
      assertThat(sessions.getSession(model.sessionId)).isNotNull()
    }

    assertThat(sessions.getSession(checkNotNull(sessionId))).isNull()
  }

  @Test
  fun `the model session closes when an assertion fails before component creation`(): Unit = timeoutRunBlocking {
    val sessions = PluginManagerSessionService.getInstance()
    var sessionId: String? = null
    val failure = AssertionError("The test assertion failed")
    val result = runCatching {
      withModel { model ->
        sessionId = model.sessionId
        assertThat(sessions.getSession(model.sessionId)).isNotNull()
        throw failure
      }
    }

    assertThat(result.exceptionOrNull()).isSameAs(failure)
    assertThat(sessions.getSession(checkNotNull(sessionId))).isNull()
  }

  private suspend fun CoroutineScope.withModel(action: suspend (MyPluginModel) -> Unit) {
    val model = withContext(Dispatchers.EDT) { createModel(this@withModel) }
    try {
      model.waitForSessionInitialization()
      action(model)
    }
    finally {
      model.waitForSessionInitialization()
      DefaultUiPluginManagerController.closeSession(model.sessionId)
    }
  }

  private suspend fun setExclusion(model: MyPluginModel, exclusion: Exclusion?) {
    model.waitForSessionInitialization()
    val facade = PluginModelFacade(model)
    withContext(Dispatchers.IO) {
      facade.persistPluginUpdateExclusion(pluginId, exclusion)
    }
    assertThat(facade.getPluginUpdateExclusion(pluginId)).isEqualTo(exclusion)
  }

  private suspend fun changeExclusion(model: MyPluginModel, exclusion: Exclusion?) {
    setExclusion(model, exclusion)
    model.updateUiAfterUpdateExclusionChange(
      pluginId,
      pluginUpdateExclusion = exclusion,
      isMissingUpdateSourceWarningEnabled = true,
    )
  }

  private fun createModel(coroutineScope: CoroutineScope): MyPluginModel {
    LafManager.getInstance()
    return MyPluginModel(null).apply {
      this.coroutineScope = coroutineScope
      setTopController(Configurable.TopComponentController.EMPTY)
    }
  }

  private fun createDetails(model: MyPluginModel, coroutineScope: CoroutineScope): PluginDetailsPageComponent {
    return PluginDetailsPageComponent(
      pluginModel = PluginModelFacade(model),
      searchListener = LinkListener { _, _ -> },
      isMarketplace = false,
      customizationStrategy = DefaultPluginDetailsPageCustomizationStrategy,
      operationLauncher = PluginOperationLauncher(coroutineScope),
      secondaryButtons = true,
      badgeTags = true,
      layout = PluginDetailsPageLayout.Unified,
      pluginsAutoUpdateEnabledProvider = { true },
    ).also(model::addDetailPanel)
  }

  private fun createRow(model: MyPluginModel, coroutineScope: CoroutineScope, plugin: PluginUiModel): ListPluginComponent {
    val listModel = ListPluginModel()
    listModel.pluginInstallationStates[pluginId] = PluginInstallationState(fullyInstalled = true)
    return ListPluginComponent(
      pluginModelFacade = PluginModelFacade(model),
      pluginUiModel = plugin,
      group = PluginsGroup("Installed", PluginsGroupType.INSTALLED),
      listModel = listModel,
      searchListener = LinkListener { _, _ -> },
      coroutineScope = coroutineScope,
      marketplace = false,
    )
  }

  private fun unknownUpdateSourceWarningCases(): List<UnknownUpdateSourceWarningCase> {
    return listOf(
      UnknownUpdateSourceWarningCase(
        name = "not bundled",
        isBundled = false,
        allowBundledUpdate = false,
        exclusion = null,
        isVisible = true,
      ),
      UnknownUpdateSourceWarningCase(
        name = "bundled and allowed to be updated",
        isBundled = true,
        allowBundledUpdate = true,
        exclusion = null,
        isVisible = true,
      ),
      UnknownUpdateSourceWarningCase(
        name = "bundled and not allowed to be updated",
        isBundled = true,
        allowBundledUpdate = false,
        exclusion = null,
        isVisible = false,
      ),
      UnknownUpdateSourceWarningCase(
        name = "manual update only",
        isBundled = false,
        allowBundledUpdate = false,
        exclusion = Exclusion.FromAutoUpdate,
        isVisible = true,
      ),
      UnknownUpdateSourceWarningCase(
        name = "ignore updates",
        isBundled = false,
        allowBundledUpdate = false,
        exclusion = Exclusion.FromUpdate,
        isVisible = false,
      ),
    )
  }

  private fun plugin(testCase: UnknownUpdateSourceWarningCase): PluginUiModel {
    return plugin(
      isBundled = testCase.isBundled,
      allowBundledUpdate = testCase.allowBundledUpdate,
    )
  }

  private fun plugin(
    isBundled: Boolean = false,
    allowBundledUpdate: Boolean = false,
  ): PluginUiModel {
    val plugin = PluginDto("Plugin", pluginId).apply {
      version = "1.0"
      description = "Plugin description"
      detailsLoaded = true
      this.isBundled = isBundled
      this.allowBundledUpdate = allowBundledUpdate
    }
    assertThat(plugin.isUpdateable).isEqualTo(!isBundled || allowBundledUpdate)
    return plugin
  }

  private suspend fun PluginDetailsPageComponent.detachOnEdt() {
    withContext(NonCancellable + Dispatchers.EDT) {
      detach()
    }
  }

  /** The unknown update source banner precedes the success banner in the details' layout. */
  private fun Container.findUnknownUpdateSourceBanner(): UpdateSourceBanner {
    return checkNotNull(UIUtil.uiTraverser(this).filter(UpdateSourceBanner::class.java).first())
  }

  private fun Container.findUnknownUpdateSourceWarning(): JTextComponent {
    val text = IdeBundle.message("plugins.configurable.plugin.list.unknown.update.source.warning")
    return UIUtil.uiTraverser(this).filter(JTextComponent::class.java).single { it.text == text }
  }
}
