// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.unified.LegacyPluginDetailsPresenter
import com.intellij.ide.plugins.unified.LegacyPluginRow
import com.intellij.ide.plugins.unified.LegacyPluginRowFactory
import com.intellij.ide.plugins.unified.PluginDetailsMode
import com.intellij.ide.plugins.unified.PluginDetailsSelection
import com.intellij.ide.plugins.unified.PluginItemModelHandle
import com.intellij.ide.plugins.unified.PluginItemState
import com.intellij.ide.plugins.unified.PluginSectionId
import com.intellij.ide.plugins.unified.PluginSectionState
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.productMode.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.OnOffButton
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Component
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel

@TestApplication
internal class PluginLoadingProblemUiTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet

  @BeforeEach
  fun rememberState() {
    originalPluginSet = PluginManagerCore.getPluginSet()
    LafManager.getInstance()
    ActionManager.getInstance()
    ApplicationManager.getApplication().replaceService(PluginInfoProvider::class.java, object : PluginInfoProvider {
      override fun loadCachedPlugins(): Set<PluginId> = emptySet()

      override fun loadPlugins(indicator: ProgressIndicator?) = CompletableFuture.completedFuture(emptySet<PluginId>())
    }, disposable)
  }

  @AfterEach
  fun restoreState() {
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  fun `product mode hints stay in rows and clear after a mode change`(): Unit = timeoutRunBlocking {
    val moduleId = PluginModuleId("mode.module", PluginModuleId.JETBRAINS_NAMESPACE)
    val id = PluginId.getId("mode.ui.plugin")
    plugin(id.idString) {
      content(namespace = "jetbrains") { module(moduleId.name, loadingRule = ModuleLoadingRuleValue.REQUIRED) {} }
    }.installAt(tempDir)
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir)
                                     .withProductMode(ProductMode.LIGHT_MONOLITH)
                                     .withEnvironmentConfiguredModules(mapOf(moduleId to PluginInitializationContext.EnvironmentConfiguredModuleData(
                                       UnsuitableProductModeModuleUnavailabilityReason(moduleId, ProductMode.LIGHT_MONOLITH.id),
                                     )))
                                     .build(configureClassLoaders = false))
    val scope = this
    val host = withContext(Dispatchers.EDT) { LegacyPluginUiHost(parentScope = scope, operationScope = scope) }
    try {
      host.awaitSessionInitialization()
      val result = DefaultUiPluginManagerController.getErrors(host.sessionId, id)
      val explanations = MyPluginModel.getExpectedExplanations(result)
      assertThat(explanations).hasSize(1)
      val model = PluginNodeModelBuilderFactory.createBuilder(id).setName("Mode UI Plugin").setIsConverted(true).build()
      val input = PluginRowInput(model, PluginInstallationState(true), MyPluginModel.getErrors(result),
                                 null, true, false, expectedExplanations = explanations)
      val item = PluginItemState(id, model.name, modelHandle = PluginItemModelHandle(model), rowInput = input)
      val (row, details) = withContext(Dispatchers.EDT) {
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), { _, _ -> }, onSelectionChanged = {})
        val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
        val specification = factory.specification(section, item)
        val row = factory.createRow(specification.occurrenceId, item, specification.renderKey) as LegacyPluginRow
        row.renderInput(input)
        val details = host.createDetails({ _, _ -> }, marketplace = false)
        details.showPlugin(row.component)
        row to details
      }
      waitUntilAssertSucceeds {
        withContext(Dispatchers.EDT) {
          assertThat(hints(row.component).filter { it.isVisible }).hasSize(1)
          assertThat(hints(details)).isEmpty()
          val rowHint = hints(row.component).single { it.isVisible }
          assertThat(rowHint.toolTipText).contains(explanations.single())
          assertThat(UIUtil.uiTraverser(row.component).filter(OnOffButton::class.java).toList().single().isSelected).isTrue()
          assertThat(UIUtil.uiTraverser(details).filter(JEditorPane::class.java).map { it.text }.toList())
            .anyMatch { it.contains(checkNotNull(model.name)) }
          assertThat(UIUtil.uiTraverser(details).filter(JEditorPane::class.java).toList())
            .noneMatch { it.text.contains(explanations.single()) }
          assertThat(UIUtil.uiTraverser(details).filter(JButton::class.java).toList())
            .noneMatch { it.accessibleContext.accessibleDescription?.contains(explanations.single()) == true }
        }
      }

      PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir)
                                       .withProductMode(ProductMode.MONOLITH)
                                       .build(configureClassLoaders = false))
      DefaultUiPluginManagerController.updatePluginDependenciesSync(host.sessionId)
      withContext(Dispatchers.EDT) {
        row.component.updateEnabledState()
        details.updateEnabledState()
      }
      waitUntilAssertSucceeds {
        withContext(Dispatchers.EDT) {
          assertThat(hints(row.component)).noneMatch { it.isVisible }
          assertThat(hints(details)).isEmpty()
          assertThat(hints(row.component).single().toolTipText).isNull()
          assertThat(UIUtil.uiTraverser(row.component).filter(OnOffButton::class.java).toList().single().isSelected).isTrue()
        }
      }
    }
    finally {
      withContext(Dispatchers.EDT) { host.dispose(closeSession = false) }
      PluginManagerSessionService.getInstance().removeSession(host.sessionId)
    }
  }

  @Test
  fun `selected details omit hints from reused rows`(): Unit = timeoutRunBlocking {
    val scope = this
    val host = withContext(Dispatchers.EDT) { LegacyPluginUiHost(parentScope = scope, operationScope = scope) }
    try {
      val id = PluginId.getId("mode.details.refresh")
      val model = PluginNodeModelBuilderFactory.createBuilder(id).setName("Mode Details Refresh").setIsConverted(true).build()
      val input = PluginRowInput(model, PluginInstallationState(true), emptyList(), null, true, false)
      val item = PluginItemState(id, model.name, modelHandle = PluginItemModelHandle(model), rowInput = input)
      val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
      val (row, presenter) = withContext(Dispatchers.EDT) {
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), { _, _ -> }, onSelectionChanged = {})
        val specification = factory.specification(section, item)
        val row = factory.createRow(specification.occurrenceId, item, specification.renderKey) as LegacyPluginRow
        row.renderInput(input)
        val presenter = LegacyPluginDetailsPresenter(host) { _, _ -> }
        presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(specification.occurrenceId, row)))
        row to presenter
      }
      try {
        waitUntilAssertSucceeds {
          withContext(Dispatchers.EDT) {
            val details =
              UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
            assertThat(UIUtil.uiTraverser(details).filter(JEditorPane::class.java).map { it.text }.toList())
              .anyMatch { it?.contains(checkNotNull(model.name)) == true }
          }
        }
        withContext(Dispatchers.EDT) {
          val details =
            UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
          val explanations = listOf("Client: Light mode is not supported.", "Host: Backend mode is not supported.")
          for (next in listOf(explanations, emptyList(), explanations)) {
            row.renderInput(input.copy(expectedExplanations = next))
            presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(section.occurrenceId(id), row)))

            assertThat(hints(row.component).single().isVisible).isEqualTo(next.isNotEmpty())
            assertThat(hints(details)).isEmpty()
            assertThat(UIUtil.uiTraverser(details).filter(JButton::class.java).toList())
              .noneMatch { it.accessibleContext.accessibleDescription?.contains("mode is not supported") == true }
          }
          presenter.render(PluginDetailsMode.LOCAL, emptyList())
        }
        waitUntilAssertSucceeds {
          withContext(Dispatchers.EDT) {
            val details =
              UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
            assertThat(hints(details)).isEmpty()
          }
        }
      }
      finally {
        withContext(Dispatchers.EDT) {
          presenter.close()
          row.close()
        }
      }
    }
    finally {
      withContext(Dispatchers.EDT) { host.dispose(closeSession = false) }
      PluginManagerSessionService.getInstance().removeSession(host.sessionId)
    }
  }

  private fun hints(component: Component): List<JLabel> =
    UIUtil.uiTraverser(component).filter(JLabel::class.java).filter { it.icon == AllIcons.General.Note }.toList()
}
