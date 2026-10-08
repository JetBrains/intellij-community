// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerButtonType
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerSide
import com.intellij.ide.plugins.newui.buttons.InstallOptionButton
import com.intellij.ide.plugins.newui.buttons.OptionButton
import com.intellij.ide.ui.LafManager
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.RegistryKey
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane

@TestApplication
internal class PluginManagerButtonUsageTest {
  @TestDisposable
  lateinit var disposable: Disposable

  companion object {
    private const val SPLIT_REGISTRY_KEY = "reworked.plugin.manager.enabled"
    private var previousSplitKey: RegistryKeyDescriptor? = null

    @JvmStatic
    @BeforeAll
    fun registerSplitRegistryKey() {
      Registry.mutateContributedKeys { keys ->
        previousSplitKey = keys[SPLIT_REGISTRY_KEY]
        keys + (SPLIT_REGISTRY_KEY to (previousSplitKey ?: RegistryKeyDescriptor(
          SPLIT_REGISTRY_KEY, "Use the combined plugin manager", "false", false, false, null, null,
        )))
      }
    }

    @JvmStatic
    @AfterAll
    fun restoreSplitRegistryKey() {
      Registry.mutateContributedKeys { keys ->
        val descriptor = previousSplitKey
        if (descriptor == null) keys - SPLIT_REGISTRY_KEY else keys + (SPLIT_REGISTRY_KEY to descriptor)
      }
    }
  }

  @Test
  fun `row install button reports a click before a cancelled operation`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val events = withContext(Dispatchers.EDT) {
        val row = createRow(host, marketplace = true)
        collectEvents(recorder) { checkNotNull(row.myInstallButton).doClick() }
      }
      assertClick(events, "plugin.install.button.clicked", PluginManagerButtonType.DEFAULT)
    }
  }

  @Test
  fun `context install reports each selected plugin and preserves button listeners`(): Unit = testRecorders { recorder ->
    withHost { host ->
      var listenerCalls = 0
      val events = withContext(Dispatchers.EDT) {
        val rows = listOf(createRow(host, marketplace = true), createRow(host, marketplace = true, id = "second.plugin"))
        rows.forEach { checkNotNull(it.myInstallButton).addActionListener { listenerCalls++ } }
        val action = DefaultActionGroup().also { rows.first().createPopupMenu(it, rows) }
          .getChildren(TestActionEvent.createTestEvent()).single()
        collectEvents(recorder) {
          action.actionPerformed(contextEvent(action))
          checkNotNull(rows.first().myInstallButton).doClick()
        }
      }
      assertThat(listenerCalls).isEqualTo(3)
      assertClick(events, "plugin.install.button.clicked", PluginManagerButtonType.CONTEXT_MENU, PluginManagerButtonType.CONTEXT_MENU,
                  PluginManagerButtonType.DEFAULT)
    }
  }

  @Test
  fun `details install button reports a click before a cancelled operation`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val details = createDetails(host, marketplace = true)
      waitUntilAssertSucceeds {
        withContext(Dispatchers.EDT) {
          assertThat(components(details).filterIsInstance<InstallOptionButton>().single().isVisible).isTrue()
          assertThat(components(details).filterIsInstance<InstallOptionButton>().single().isEnabled).isTrue()
        }
      }
      val events = withContext(Dispatchers.EDT) {
        collectEvents(recorder) { components(details).filterIsInstance<InstallOptionButton>().single().doClick() }
      }
      assertClick(events, "plugin.install.button.clicked", PluginManagerButtonType.DEFAULT)
    }
  }

  @Test
  fun `uninstall dropdown reports a click before a cancelled operation`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val details = createDetails(host, marketplace = false)
      waitUntilAssertSucceeds {
        withContext(Dispatchers.EDT) {
          assertThat(components(details).filterIsInstance<OptionButton>().single { it !is InstallOptionButton }.isVisible).isTrue()
        }
      }
      val events = withContext(Dispatchers.EDT) {
        val button = components(details).filterIsInstance<OptionButton>().single { it !is InstallOptionButton }
        val uninstall = checkNotNull(button.options).single()
        collectEvents(recorder) { uninstall.actionPerformed(ActionEvent(button, ActionEvent.ACTION_PERFORMED, "")) }
      }
      assertClick(events, "plugin.uninstall.button.clicked", PluginManagerButtonType.DROPDOWN)
    }
  }

  @Test
  fun `standalone uninstall button reports a default click`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val details = createDetails(host, marketplace = false)
      waitUntilAssertSucceeds {
        withContext(Dispatchers.EDT) {
          assertThat(components(details).filterIsInstance<JButton>()
                       .any { it.text == IdeBundle.message("plugins.configurable.uninstall") }).isTrue()
        }
      }
      val events = withContext(Dispatchers.EDT) { collectEvents(recorder) { uninstallButton(details).doClick() } }
      assertClick(events, "plugin.uninstall.button.clicked", PluginManagerButtonType.DEFAULT)
    }
  }

  @Test
  fun `context uninstall reports each selected plugin`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val events = withContext(Dispatchers.EDT) {
        val rows = listOf(createRow(host, marketplace = false), createRow(host, marketplace = false, id = "second.plugin"))
        val action = DefaultActionGroup().also { rows.first().createPopupMenu(it, rows) }
          .getChildren(TestActionEvent.createTestEvent()).filterIsInstance<UninstallAction<*>>().single()
        collectEvents(recorder) { action.actionPerformed(contextEvent(action)) }
      }
      assertClick(events, "plugin.uninstall.button.clicked", PluginManagerButtonType.CONTEXT_MENU, PluginManagerButtonType.CONTEXT_MENU)
    }
  }

  @Test
  fun `keyboard install and uninstall report default clicks`(): Unit = testRecorders { recorder ->
    withHost { host ->
      val events = withContext(Dispatchers.EDT) {
        val available = createRow(host, marketplace = true)
        val installed = createRow(host, marketplace = false, id = "installed.plugin")
        collectEvents(recorder) {
          available.handleKeyAction(KeyEvent(available, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'), listOf(available))
          installed.handleKeyAction(
            KeyEvent(installed, KeyEvent.KEY_PRESSED, 0, 0, EventHandler.DELETE_CODE, KeyEvent.CHAR_UNDEFINED), listOf(installed),
          )
        }
      }
      assertClick(events, "plugin.install.button.clicked", PluginManagerButtonType.DEFAULT)
      assertClick(events, "plugin.uninstall.button.clicked", PluginManagerButtonType.DEFAULT)
    }
  }

  @Test
  @RegistryKey(key = SPLIT_REGISTRY_KEY, value = "true")
  fun `active customizer suppresses local button reporting`(): Unit = testRecorders { recorder ->
    val application = ApplicationManager.getApplication()
    val key = FrontendApplicationInfo.frontendTypeDataKey
    val previousFrontend = application.getUserData(key)
    val customizer = EnabledCustomizer()
    val customizerDisposable = Disposer.newDisposable(disposable, "Customizer $recorder")
    try {
      ExtensionTestUtil.maskExtensions(PluginManagerCustomizer.EP_NAME, listOf(customizer), customizerDisposable)
      application.putUserData(key, FrontendType.Remote(FrontendType.RemoteFrontendType.Controller))
      assertThat(PluginManagerCustomizer.getInstance()).isSameAs(customizer)
      withHost { host ->
        val details = createDetails(host, marketplace = true)
        val installedDetails = createDetails(host, marketplace = false)
        val events = withContext(Dispatchers.EDT) {
          val available = createRow(host, marketplace = true)
          val installed = createRow(host, marketplace = false)
          val uninstall = DefaultActionGroup().also { installed.createPopupMenu(it, listOf(installed)) }
            .getChildren(TestActionEvent.createTestEvent()).filterIsInstance<UninstallAction<*>>().single()
          val optionButton = components(installedDetails).filterIsInstance<OptionButton>().single { it !is InstallOptionButton }
          collectEvents(recorder) {
            checkNotNull(available.myInstallButton).doClick()
            components(details).filterIsInstance<InstallOptionButton>().single().doClick()
            uninstall.actionPerformed(contextEvent(uninstall))
            checkNotNull(optionButton.options).single().actionPerformed(ActionEvent(optionButton, ActionEvent.ACTION_PERFORMED, ""))
          }
        }
        assertThat(events.filter { it.event.id == "plugin.install.button.clicked" || it.event.id == "plugin.uninstall.button.clicked" })
          .isEmpty()
      }
    }
    finally {
      application.putUserData(key, previousFrontend)
      Disposer.dispose(customizerDisposable)
    }
  }

  private fun testRecorders(action: suspend CoroutineScope.(String) -> Unit): Unit = timeoutRunBlocking {
    for (recorder in listOf("FUS", "MP")) {
      action(recorder)
    }
  }

  private suspend fun CoroutineScope.withHost(action: suspend (LegacyPluginUiHost) -> Unit) {
    val operationScope = childScope("Cancelled plugin operations").apply { cancel() }
    val host = withContext(Dispatchers.EDT) {
      LafManager.getInstance()
      LegacyPluginUiHost(parentScope = this@withHost, operationScope = operationScope)
    }
    try {
      host.awaitSessionInitialization()
      action(host)
    }
    finally {
      withContext(Dispatchers.EDT) { host.dispose(closeSession = true) }
    }
  }

  private fun createRow(host: LegacyPluginUiHost, marketplace: Boolean, id: String = "button.usage.plugin"): ListPluginComponent {
    val pluginId = PluginId.getId(id)
    val plugin = PluginNodeModelBuilderFactory.createBuilder(pluginId)
      .setName("Button Usage Plugin").setDescription("Description").setIsConverted(true).build()
    val group = PluginsGroup("Plugins", if (marketplace) PluginsGroupType.SEARCH else PluginsGroupType.INSTALLED)
    group.addModel(plugin)
    val listModel = ListPluginModel().apply {
      if (!marketplace) {
        installedModels[pluginId] = plugin
        setPluginInstallationState(pluginId, PluginInstallationState(true))
      }
    }
    return host.createRow(plugin, group, listModel, { _, _ -> }, marketplace)
  }

  private suspend fun createDetails(host: LegacyPluginUiHost, marketplace: Boolean): PluginDetailsPageComponent {
    val details = withContext(Dispatchers.EDT) {
      host.createDetails({ _, _ -> }, marketplace).also { details ->
        details.getValue(0, true)
        details.showPlugin(createRow(host, marketplace))
      }
    }
    waitUntilAssertSucceeds {
      withContext(Dispatchers.EDT) {
        assertThat(components(details).filterIsInstance<JEditorPane>().any { it.text.contains("Button Usage Plugin") }).isTrue()
      }
    }
    return details
  }

  private fun collectEvents(recorder: String, action: () -> Unit): List<LogEvent> {
    val recorderDisposable = Disposer.newDisposable(disposable, recorder)
    try {
      return FUCollectorTestCase.collectLogEvents(recorder, recorderDisposable, escapeChars = true) {
        PluginManagerUsageCollector.logSessionStarted()
        action()
      }
    }
    finally {
      Disposer.dispose(recorderDisposable)
    }
  }

  private fun assertClick(events: List<LogEvent>, eventId: String, vararg buttonTypes: PluginManagerButtonType) {
    val session = events.single { it.event.id == "session.started" }
    val clicks = events.filter { it.event.id == eventId }
    assertThat(clicks.map { it.event.data["button_type"] }).containsExactly(*buttonTypes.map(Enum<*>::name).toTypedArray())
    clicks.forEach { click ->
      assertThat(click.event.data["side"]).isEqualTo(PluginManagerSide.FRONTEND.name)
      assertThat(click.event.data["sessionId"]).isEqualTo(session.event.data["sessionId"])
      assertThat(click.event.data["searchSessionId"]).isEqualTo(session.event.data["searchSessionId"])
    }
    assertThat(events.filter { it.event.id == "plugin.installation.started" || it.event.id == "plugin.was.removed" }).isEmpty()
  }

  private fun contextEvent(action: AnAction): AnActionEvent =
    AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, action.templatePresentation.clone(), "", ActionUiKind.POPUP, null)

  private fun uninstallButton(details: PluginDetailsPageComponent): JButton =
    components(details).filterIsInstance<JButton>().single { it.text == IdeBundle.message("plugins.configurable.uninstall") }

  private fun components(component: Component): List<Component> =
    listOf(component) + if (component is Container) component.components.flatMap(::components) else emptyList()
}

private class EnabledCustomizer : PluginManagerCustomizer {
  override fun isEnabled(): Boolean = true
  override fun initCustomizer(parentComponent: JComponent) {}
  override suspend fun getInstallButonCustomizationModel(
    pluginModelFacade: PluginModelFacade, pluginToInstallModel: PluginUiModel, modalityState: ModalityState,
  ): OptionsButonCustomizationModel? = null
  override suspend fun getDisableButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade, pluginModel: PluginUiModel, modalityState: ModalityState,
  ): OptionsButonCustomizationModel? = null
  override suspend fun getUpdateButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade, pluginModel: PluginUiModel, updateModel: PluginUiModel?, modalityState: ModalityState,
  ): UpdateButtonCustomizationModel? = null
  override suspend fun getUninstallButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade, pluginModel: PluginUiModel,
  ): UninstallButtonCustomizationModel? = null
  override fun updateAfterModification(updateUi: () -> Unit) = updateUi()
  override suspend fun updateAfterModificationAsync(updateUi: suspend () -> Unit) = updateUi()
  override fun getExtraPluginsActions(): List<AnAction> = emptyList()
  override fun onPluginDeleted(pluginModel: PluginUiModel, pluginSource: PluginSource) {}
  override fun getAdditionalTitleText(pluginModel: PluginUiModel): String? = null
  override fun getUpdateSourceText(pluginModel: PluginUiModel): String? = null
  override suspend fun awaitPluginStatesLoaded() {}
  override fun updateCustomRepositories(addedRepoUrls: List<String>, removedRepoUrls: List<String>, updateUi: () -> Unit) = updateUi()
  override fun requestRestart(pluginModelFacade: PluginModelFacade, parentComponent: JComponent?) {}
  override fun getAllPluginIds(pluginId: PluginId): Set<PluginId> = setOf(pluginId)
}
