// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginLogo
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
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.OnOffButton
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.text.LabelView
import javax.swing.text.Position
import kotlin.time.Duration.Companion.seconds

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
  fun `product mode exclusions gray rows and show details explanations until the mode changes`(): Unit = checkExpectedExclusion("product mode")

  @Test
  fun `custom plugin restrictions use expected presentation until the restriction clears`(): Unit = checkExpectedExclusion("custom plugins")

  @Test
  fun `subset restrictions use expected presentation until the restriction clears`(): Unit = checkExpectedExclusion("configured subset")

  @Test
  fun `core-only restrictions use expected presentation until the restriction clears`(): Unit = checkExpectedExclusion("only core")

  private fun checkExpectedExclusion(exclusion: String): Unit = timeoutRunBlocking {
    val moduleId = PluginModuleId("mode.module", PluginModuleId.JETBRAINS_NAMESPACE)
    val id = PluginId.getId("mode.ui.plugin")
    plugin(id.idString) {
      content(namespace = "jetbrains") { module(moduleId.name, loadingRule = ModuleLoadingRuleValue.REQUIRED) {} }
    }.installAt(tempDir)
    val builder = PluginSetTestBuilder.fromPath(tempDir)
    val baseContext = builder.withProductMode(ProductMode.LIGHT_MONOLITH)
      .withEnvironmentConfiguredModules(mapOf(moduleId to PluginInitializationContext.EnvironmentConfiguredModuleData(
        if (exclusion == "product mode") UnsuitableProductModeModuleUnavailabilityReason(moduleId, ProductMode.LIGHT_MONOLITH.id) else null,
      )))
      .buildInitContext()
    val initContext = when (exclusion) {
      "product mode" -> baseContext
      "configured subset" -> object : PluginInitializationContext by baseContext {
        override val explicitPluginSubsetToLoad: Set<PluginId> = emptySet()
      }
      "only core" -> object : PluginInitializationContext by baseContext {
        override val disablePluginLoadingCompletely: Boolean = true
      }
      "custom plugins" -> object : PluginInitializationContext by baseContext {
        override fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet) =
          pluginSet.plugins.asSequence()
            .filter { it.pluginId != PluginManagerCore.CORE_ID }
            .map { it to NonBundledPluginsLoadingIsDisabled }
      }
      else -> error("Unknown exclusion: $exclusion")
    }
    PluginManagerCore.setPluginSet(PluginManagerCore.initializePlugins(
      initContext = initContext,
      discoveredPlugins = builder.discoverPlugins().second,
      coreLoader = UrlClassLoader.build().get(),
      parentActivity = null,
      configureClassLoaders = false,
    ))
    val state = checkNotNull(PluginManagerStateService().getCurrentState(
      PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    ))
    val problem = checkNotNull(state.pluginNonLoadReasons[id])
    val expectedMessage = when (exclusion) {
      "product mode" -> "This plugin does not support Light mode"
      "custom plugins" -> "Loading of custom plugins is disabled"
      "configured subset" -> "Not required"
      "only core" -> "Loading of plugins is disabled"
      else -> error("Unknown exclusion: $exclusion")
    }
    assertThat(problem.shortMessage).isEqualTo(expectedMessage)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
    assertThat(problem.shouldNotifyUser).isFalse()
    assertThat(PluginManagerCore.getPluginSet().enabledPlugins.map { it.pluginId }).doesNotContain(id)
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
    val scope = this
    val host = withContext(Dispatchers.EDT) { LegacyPluginUiHost(parentScope = scope, operationScope = scope) }
    try {
      host.awaitSessionInitialization()
      val result = DefaultUiPluginManagerController.getErrors(host.sessionId, id)
      val explanations = MyPluginModel.getExpectedExplanations(result)
      assertThat(explanations).containsExactly(expectedMessage)
      assertThat(MyPluginModel.getErrors(result)).isEmpty()
      assertThat(result.suggestToEnableRequiredPlugins).isFalse()
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
      waitUntilAssertSucceeds(timeout = 3.seconds) {
        withContext(Dispatchers.EDT) {
          assertThat(hints(row.component)).isEmpty()
          assertThat(hints(details)).isEmpty()
          val title = UIUtil.uiTraverser(row.component).filter(JLabel::class.java).toList().single { it.text == model.name }
          val icon = UIUtil.uiTraverser(row.component).filter(JLabel::class.java).toList().single { it.icon != null }
          val toggle = UIUtil.uiTraverser(row.component).filter(OnOffButton::class.java).toList().single()
          assertThat(title.foreground).isEqualTo(ListPluginComponent.DisabledColor)
          assertThat(iconPixels(icon.icon)).isEqualTo(iconPixels(PluginLogo.getIcon(model.getDescriptor(), false, false, true)))
          assertThat(toggle.isSelected).isTrue()
          assertThat(toggle.isEnabled).isTrue()
          assertThat(UIUtil.uiTraverser(details).filter(JEditorPane::class.java).map { it.text }.toList())
            .anyMatch { it.contains(checkNotNull(model.name)) }
          val problem = problemPanes(details).single { it.text.contains(explanations.single()) }
          assertThat(textForeground(problem, explanations.single())).isEqualTo(UIUtil.getContextHelpForeground())
          assertThat(problem.document.getText(0, problem.document.length)).contains(explanations.single())
          assertThat(UIUtil.uiTraverser(details).filter(JButton::class.java).toList())
            .noneMatch { it.accessibleContext.accessibleDescription?.contains(explanations.single()) == true }
        }
      }
      assertThat(host.getPluginEnabledStates(listOf(model))[id]).isTrue()

      PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir)
                                       .withProductMode(ProductMode.MONOLITH)
                                       .build(configureClassLoaders = false))
      DefaultUiPluginManagerController.updatePluginDependenciesSync(host.sessionId)
      withContext(Dispatchers.EDT) {
        row.component.updateEnabledState()
        details.updateEnabledState()
      }
      waitUntilAssertSucceeds(timeout = 3.seconds) {
        withContext(Dispatchers.EDT) {
          assertThat(hints(row.component)).isEmpty()
          assertThat(hints(details)).isEmpty()
          val title = UIUtil.uiTraverser(row.component).filter(JLabel::class.java).toList().single { it.text == model.name }
          val icon = UIUtil.uiTraverser(row.component).filter(JLabel::class.java).toList().single { it.icon != null }
          val toggle = UIUtil.uiTraverser(row.component).filter(OnOffButton::class.java).toList().single()
          assertThat(title.foreground).isNotEqualTo(ListPluginComponent.DisabledColor)
          assertThat(iconPixels(icon.icon)).isEqualTo(iconPixels(PluginLogo.getIcon(model.getDescriptor(), false, false, false)))
          assertThat(toggle.isSelected).isTrue()
          assertThat(toggle.isEnabled).isTrue()
          assertThat(problemPanes(details)).noneMatch { it.text.contains(explanations.single()) }
        }
      }
      assertThat(host.getPluginEnabledStates(listOf(model))[id]).isTrue()
    }
    finally {
      withContext(Dispatchers.EDT) { host.dispose(closeSession = false) }
      PluginManagerSessionService.getInstance().removeSession(host.sessionId)
    }
  }

  @Test
  fun `selected details refresh expected explanations without hiding errors`(): Unit = timeoutRunBlocking {
    val id = PluginId.getId("mode.details.refresh")
    val healthyId = PluginId.getId("mode.details.healthy")
    plugin(id.idString) { depends("missing.for.ui") }.installAt(tempDir)
    plugin(healthyId.idString) {}.installAt(tempDir)
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir).build(configureClassLoaders = false))
    val scope = this
    val host = withContext(Dispatchers.EDT) { LegacyPluginUiHost(parentScope = scope, operationScope = scope) }
    try {
      host.awaitSessionInitialization()
      val result = DefaultUiPluginManagerController.getErrors(host.sessionId, id)
      val model = PluginNodeModelBuilderFactory.createBuilder(id).setName("Mode Details Refresh").setIsConverted(true).build()
      val input = PluginRowInput(model, PluginInstallationState(true), MyPluginModel.getErrors(result), null, true, false)
      val item = PluginItemState(id, model.name, modelHandle = PluginItemModelHandle(model), rowInput = input)
      val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
      val (row, healthyRow, presenter) = withContext(Dispatchers.EDT) {
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), { _, _ -> }, onSelectionChanged = {})
        val specification = factory.specification(section, item)
        val row = factory.createRow(specification.occurrenceId, item, specification.renderKey) as LegacyPluginRow
        row.renderInput(input)
        val healthyModel = PluginNodeModelBuilderFactory.createBuilder(healthyId).setName("Healthy Plugin").setIsConverted(true).build()
        val healthyItem = PluginItemState(healthyId, healthyModel.name, modelHandle = PluginItemModelHandle(healthyModel))
        val healthySpecification = factory.specification(section.copy(items = listOf(healthyItem)), healthyItem)
        val healthyRow = factory.createRow(healthySpecification.occurrenceId, healthyItem, healthySpecification.renderKey) as LegacyPluginRow
        val presenter = LegacyPluginDetailsPresenter(host) { _, _ -> }
        presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(specification.occurrenceId, row)))
        Triple(row, healthyRow, presenter)
      }
      try {
        waitUntilAssertSucceeds {
          withContext(Dispatchers.EDT) {
            val details =
              UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
            assertThat(problemPanes(details)).anyMatch { it.text.contains("missing.for.ui") }
          }
        }
        withContext(Dispatchers.EDT) {
          val details =
            UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
          val explanations = listOf("Client: Light mode is not supported <here>.", "Host: Backend mode is not supported.")
          val originalProblem = problemPanes(details).single { it.text.contains("missing.for.ui") }
          for (next in listOf(explanations, emptyList(), explanations)) {
            row.renderInput(input.copy(expectedExplanations = next))
            presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(section.occurrenceId(id), row)))

            assertThat(hints(row.component)).isEmpty()
            assertThat(hints(details)).isEmpty()
            val problem = problemPanes(details).single { it.text.contains("missing.for.ui") }
            assertThat(problem).isSameAs(originalProblem)
            assertThat(textForeground(problem, "missing.for.ui")).isEqualTo(NamedColorUtil.getErrorForeground())
            val text = problem.document.getText(0, problem.document.length)
            if (next.isEmpty()) {
              assertThat(text).doesNotContain("mode is not supported")
            }
            else {
              assertThat(text).contains(explanations[0], explanations[1])
              assertThat(problem.text).contains("&lt;here&gt;")
              assertThat(textForeground(problem, explanations[0])).isEqualTo(UIUtil.getContextHelpForeground())
              assertThat(textForeground(problem, explanations[1])).isEqualTo(UIUtil.getContextHelpForeground())
            }
            assertThat(UIUtil.uiTraverser(details).filter(JButton::class.java).toList())
              .noneMatch { it.accessibleContext.accessibleDescription?.contains("mode is not supported") == true }
          }
          presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(section.occurrenceId(healthyId), healthyRow)))
        }
        waitUntilAssertSucceeds {
          withContext(Dispatchers.EDT) {
            val details =
              UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
            assertThat(details.descriptorForActions?.pluginId).isEqualTo(healthyId)
            assertThat(problemPanes(details)).noneMatch { it.text.contains("mode is not supported") || it.text.contains("missing.for.ui") }
          }
        }
        withContext(Dispatchers.EDT) {
          presenter.render(PluginDetailsMode.LOCAL, emptyList())
        }
        waitUntilAssertSucceeds {
          withContext(Dispatchers.EDT) {
            val details =
              UIUtil.uiTraverser(presenter.component).filter(PluginDetailsPageComponent::class.java).toList().single { it.isVisible }
            assertThat(hints(details)).isEmpty()
            assertThat(problemPanes(details)).noneMatch { it.text.contains("mode is not supported") || it.text.contains("missing.for.ui") }
          }
        }
      }
      finally {
        withContext(Dispatchers.EDT) {
          presenter.close()
          row.close()
          healthyRow.close()
        }
      }
    }
    finally {
      withContext(Dispatchers.EDT) { host.dispose(closeSession = false) }
      PluginManagerSessionService.getInstance().removeSession(host.sessionId)
    }
  }

  private fun iconPixels(icon: Icon): IntArray {
    val image = ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      icon.paintIcon(JLabel(), graphics, 0, 0)
    }
    finally {
      graphics.dispose()
    }
    return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  }

  private fun hints(component: Component): List<JLabel> =
    UIUtil.uiTraverser(component).filter(JLabel::class.java).filter { it.icon == AllIcons.General.Note }.toList()

  private fun problemPanes(component: Component): List<JEditorPane> =
    UIUtil.uiTraverser(component).filter(JEditorPane::class.java).filter { it.isVisible }.toList()

  private fun textForeground(pane: JEditorPane, text: String): Color {
    val offset = pane.document.getText(0, pane.document.length).indexOf(text)
    assertThat(offset).isGreaterThanOrEqualTo(0)
    var view = pane.ui.getRootView(pane)
    view.setSize(600f, 1000f)
    while (view.viewCount > 0) {
      view = view.getView(view.getViewIndex(offset, Position.Bias.Forward))
    }
    return (view as LabelView).foreground
  }
}
