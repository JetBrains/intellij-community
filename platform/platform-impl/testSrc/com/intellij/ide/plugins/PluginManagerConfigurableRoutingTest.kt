// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.newEditor.SettingsFilter
import com.intellij.openapi.options.newEditor.SpotlightPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.DarculaSearchFieldWithExtensionBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities

@TestApplication
internal class PluginManagerConfigurableRoutingTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "false")
  fun `disabled flag creates legacy session`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val session = createPluginsPageSession(null, PluginManagerOpenSourceEnum.OTHER)
    try {
      assertThat(session).isInstanceOf(PluginManagerConfigurablePanel::class.java)
    }
    finally {
      Disposer.dispose(session)
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `enabled flag creates unified session`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val session = createPluginsPageSession(null, PluginManagerOpenSourceEnum.OTHER)
    try {
      assertThat(session).isInstanceOf(UnifiedPluginsPageSession::class.java)
    }
    finally {
      Disposer.dispose(session)
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified enableSearch applies only when its action runs`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        val action = settingsSearchAction(configurable, "Settings query")!!
        val searchField = searchField(configurable)

        assertThat(searchField.text).isEmpty()

        action.run()

        assertThat(searchField.text).isEqualTo("Settings query")
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified navigation ignores Spotlight refresh until Settings search starts`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        configurable.navigateToMarketplace("Marketplace query")
        val spotlight = SpotlightSearchDriver(configurable, this)

        spotlight.update("")
        spotlight.update("")

        assertThat(searchField(configurable).text).isEqualTo("Marketplace query")

        spotlight.update("Settings query")
        assertThat(searchField(configurable).text).isEqualTo("Settings query")

        spotlight.update("")

        assertThat(searchField(configurable).text).isEmpty()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified Installed navigation applies immediately`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        configurable.navigateToInstalled("Installed query")

        assertThat(searchField(configurable).text).isEqualTo("Installed query")
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `action from a disposed unified session does not affect a new session`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        val oldAction = settingsSearchAction(configurable, "Old query")!!
        configurable.disposeUIResources()
        val newSearchField = searchField(configurable)

        oldAction.run()

        assertThat(newSearchField.text).isEmpty()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "false")
  fun `legacy Installed search remains deferred`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        val action = configurable.openInstalledTabWithSearch("Installed query")!!

        assertThat(configurable.isInstalledTabShowing()).isTrue()
        assertThat(settingsSearchAction(configurable, "")).isNull()

        action.run()

        assertThat(settingsSearchAction(configurable, "")).isNotNull()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  fun `registry changes do not replace an open session`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val enabledOverride = Disposer.newDisposable(disposable, "unified page enabled")
      Registry.get(UnifiedPluginsPageFeature.REGISTRY_KEY).setValue(true, enabledOverride)
      val configurable = PluginManagerConfigurable()
      try {
        val firstComponent = configurable.createComponent()
        Disposer.dispose(enabledOverride)

        assertThat(configurable.createComponent()).isSameAs(firstComponent)
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified page hides shared header status controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val configurable = PluginManagerConfigurable()
    try {
      val controller = RecordingTopController()

      configurable.getCenterComponent(controller)

      assertThat(controller.progressIndicatorVisible).isFalse()
      assertThat(controller.resetActionVisible).isFalse()
      assertThat(controller.centerComponentGap).isEqualTo(JBUI.scale(-2))
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `standalone unified page aligns the Islands search border with section titles`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val session = createPluginsPageSession(null, PluginManagerOpenSourceEnum.NOTIFICATION, isStandaloneConfigurable = true)
      try {
        val controller = RecordingTopController()
        val header = session.getCenterComponent(controller)
        val content = session.getComponent()
        content.size = content.preferredSize
        layoutRecursively(content)

        val search = header.components.first() as SearchFieldWithExtension
        val installedTitle = checkNotNull(UIUtil.uiTraverser(content).find {
          it is JBLabel && it.text == IdeBundle.message("plugin.manager.tab.installed")
        } as? JBLabel)
        val sectionTitleX = SwingUtilities.convertPoint(installedTitle, Point(), content).x
        val searchBorderInset = DarculaSearchFieldWithExtensionBorder().getBorderInsets(search).left

        assertThat(controller.centerComponentGap).isEqualTo(JBUI.scale(13))
        assertThat(controller.centerComponentGap!! + searchBorderInset).isEqualTo(sectionTitleX)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "false")
  fun `legacy page keeps shared header status controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val configurable = PluginManagerConfigurable()
    try {
      val controller = RecordingTopController()

      configurable.getCenterComponent(controller)

      assertThat(controller.progressIndicatorVisible).isNull()
      assertThat(controller.resetActionVisible).isNull()
      assertThat(controller.centerComponentGap).isNull()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified header keeps controls after the title`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val configurable = PluginManagerConfigurable()
    try {
      val header = configurable.getCenterComponent(Configurable.TopComponentController.EMPTY)
      val layout = header.layout as GridBagLayout
      val search = header.components[0]
      val updateAll = header.components[1]
      val settings = header.components[2]

      assertThat(header.border).isNull()
      assertThat(layout.getConstraints(search).insets.right).isEqualTo(JBUI.scale(8))
      assertThat(layout.getConstraints(updateAll).insets.right).isEqualTo(JBUI.scale(8))
      assertThat(layout.getConstraints(settings).weightx).isEqualTo(1.0)
      assertThat(layout.getConstraints(settings).anchor).isEqualTo(GridBagConstraints.WEST)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified session supports content before center construction`() =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      assertComponentConstructionOrder(createContentFirst = true)
    }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified session supports center before content construction`() =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      assertComponentConstructionOrder(createContentFirst = false)
    }

  private fun assertComponentConstructionOrder(createContentFirst: Boolean) {
    val configurable = PluginManagerConfigurable()
    try {
      val firstContent: JComponent
      val firstCenter: JComponent
      if (createContentFirst) {
        firstContent = configurable.createComponent()
        firstCenter = configurable.getCenterComponent(Configurable.TopComponentController.EMPTY)
      }
      else {
        firstCenter = configurable.getCenterComponent(Configurable.TopComponentController.EMPTY)
        firstContent = configurable.createComponent()
      }

      assertThat(configurable.createComponent()).isSameAs(firstContent)
      assertThat(configurable.getCenterComponent(Configurable.TopComponentController.EMPTY)).isSameAs(firstCenter)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  private fun searchField(configurable: PluginManagerConfigurable): SearchTextField {
    return checkNotNull(UIUtil.uiTraverser(configurable.topComponent).filter(SearchTextField::class.java).single())
  }

  private fun settingsSearchAction(configurable: PluginManagerConfigurable, query: String): Runnable? {
    return (configurable as SearchableConfigurable).enableSearch(query)
  }

  private class SpotlightSearchDriver(
    private val configurable: PluginManagerConfigurable,
    coroutineScope: CoroutineScope,
  ) {
    private val component = configurable.createComponent()
    private val search = SearchTextField()
    private val filter = object : SettingsFilter(null, emptyList(), search, coroutineScope) {
      override fun getConfigurable(node: SimpleNode?): Configurable? = null

      override fun findNode(configurable: Configurable?): SimpleNode? = null

      override fun updateSpotlight(now: Boolean) = Unit
    }
    private val painter = SpotlightPainter(component) {}

    fun update(query: String) {
      filter.setFilterText(query)
      painter.update(filter, configurable, component)
    }
  }

  private fun layoutRecursively(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layoutRecursively)
  }

  private class RecordingTopController : Configurable.TopComponentController {
    var progressIndicatorVisible: Boolean? = null
    var resetActionVisible: Boolean? = null
    var centerComponentGap: Int? = null

    override fun setLeftComponent(component: Component?) = Unit

    override fun showProgress(start: Boolean) = Unit

    override fun showProject(hasProject: Boolean) = Unit

    override fun showProgressIndicator(show: Boolean) {
      progressIndicatorVisible = show
    }

    override fun showResetAction(show: Boolean) {
      resetActionVisible = show
    }

    override fun setCenterComponentGap(gap: Int) {
      centerComponentGap = gap
    }
  }
}
