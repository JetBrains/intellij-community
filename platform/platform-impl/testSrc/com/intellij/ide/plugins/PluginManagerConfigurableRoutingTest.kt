// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent

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
