// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.breadcrumbs.Breadcrumbs
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs

@TestApplication
@RegistryKey(key = SimpleBanner.CENTERED_HEADER_KEY, value = "true")
@Timeout(30)
internal class SettingsHeaderLayoutTest {
  @Test
  fun `settings title aligns with plugin and history controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val title = createTitle()
    val banner = createBanner(title, resetEnabled = false)
    val search = fixedComponent(340, 30)
    val gear = fixedComponent(24, 24)
    val topComponent = JPanel(GridBagLayout()).apply {
      isOpaque = false
      add(search, GridBagConstraints().apply {
        gridx = 0
        anchor = GridBagConstraints.CENTER
        insets = JBUI.insetsRight(8)
      })
      add(gear, GridBagConstraints().apply {
        gridx = 1
        weightx = 1.0
        anchor = GridBagConstraints.WEST
      })
    }
    val historyToolbar = fixedComponent(48, 26)
    val controller = ConfigurableController()
    controller.showProgressIndicator(false)
    controller.setCenterComponentGap(JBUI.scale(-2))
    controller.setBanner(banner)
    banner.setCenterComponent(topComponent)

    val header = SettingsEditor.createHeaderPanel(banner, historyToolbar)
    val previousHeaderHeight = search.preferredSize.height + JBUI.scale(11)
    assertThat(abs(header.preferredSize.height - previousHeaderHeight)).isLessThanOrEqualTo(1)

    layoutAtPreferredSize(header, extraWidth = 200)

    assertSameVerticalCenter(header, title, search, gear, historyToolbar)
    assertComponentGap(header, title, search, expected = -2)
    assertActionGap(header, search, gear)
  }

  @Test
  fun `settings status controls stay centered without a normal top component`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    for (topComponent in listOf<JComponent?>(null, fixedComponent(0, 0))) {
      val title = createTitle()
      val banner = createBanner(title)
      val historyToolbar = fixedComponent(48, 26)
      banner.setProjectText("Project")
      banner.showProgress(true)
      banner.setCenterComponent(topComponent)
      val header = SettingsEditor.createHeaderPanel(banner, historyToolbar)

      layoutAtPreferredSize(header)

      val leftPanel = banner.getComponent(0) as Container
      val visibleLeftComponents = leftPanel.components.filter(Component::isVisible)
      assertThat(visibleLeftComponents).isNotEmpty
      assertSameVerticalCenter(header, title, historyToolbar, *visibleLeftComponents.toTypedArray())
    }
  }

  @Test
  fun `single editor controls use the fixed banner center`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val banner = object : SimpleBanner() {
      override fun getPreferredLeftPanelSize(size: Dimension): Dimension = Dimension(size.width, JBUI.scale(41))
    }
    val leftComponent = fixedComponent(80, 15)
    val centerComponent = fixedComponent(120, 30)
    banner.setLeftComponent(leftComponent)
    banner.setCenterComponent(centerComponent)

    layoutAtPreferredSize(banner)

    val leftPanel = banner.getComponent(0) as Container
    val visibleLeftComponents = leftPanel.components.filter(Component::isVisible)
    assertSameVerticalCenter(banner, centerComponent, *visibleLeftComponents.toTypedArray())
  }

  @Test
  fun `reset action follows the current configurable request`() {
    val pluginController = ConfigurableController()
    pluginController.showResetAction(false)

    assertThat(SettingsEditor.isResetActionEnabled(pluginController, true, false)).isFalse()
    assertThat(SettingsEditor.isResetActionEnabled(ConfigurableController(), true, false)).isTrue()
  }

  @Test
  fun `progress indicator follows the current configurable request`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val banner = createBanner(createTitle())
    val progress = (banner.getComponent(0) as Container).components.last()
    val controller = ConfigurableController()
    controller.setBanner(banner)

    controller.showProgressIndicator(false)
    controller.showProgress(true)
    assertThat(progress.isVisible).isFalse()

    controller.setBanner(null)
    val defaultController = ConfigurableController()
    defaultController.setBanner(banner)
    defaultController.showProgress(true)
    assertThat(progress.isVisible).isTrue()
  }

  @Test
  @RegistryKey(key = SimpleBanner.CENTERED_HEADER_KEY, value = "false")
  fun `registry fallback restores baseline layout and toolbar insets`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val title = createTitle()
    val banner = createBanner(title)
    val topComponent = fixedComponent(340, 30)
    val historyToolbar = fixedComponent(48, 26)
    banner.setCenterComponent(topComponent)
    val header = SettingsEditor.createHeaderPanel(banner, historyToolbar)

    layoutAtPreferredSize(header)

    val bannerInsets = banner.border.getBorderInsets(banner)
    assertThat(bannerInsets.top).isEqualTo(JBUI.scale(11))
    assertThat(bannerInsets.bottom).isZero
    val toolbarConstraints = (header.layout as GridBagLayout).getConstraints(historyToolbar)
    assertThat(toolbarConstraints.anchor).isEqualTo(GridBagConstraints.NORTH)
    assertThat(toolbarConstraints.insets).isEqualTo(JBUI.insets(8, 2, 0, 0))

    val leftPanel = banner.getComponent(0) as Container
    val resetLink = leftPanel.components.filterIsInstance<ActionLink>().single()
    assertThat(abs(componentBaseline(title, header) - componentBaseline(resetLink, header))).isLessThanOrEqualTo(1)
  }

  private fun createBanner(title: JComponent, resetEnabled: Boolean = true): ConfigurableEditorBanner {
    val resetAction = object : AbstractAction("Reset") {
      override fun actionPerformed(event: ActionEvent?) = Unit
    }.apply { isEnabled = resetEnabled }
    return ConfigurableEditorBanner(resetAction, title)
  }

  private fun createTitle(): Breadcrumbs {
    return object : Breadcrumbs() {
      override fun getFontStyle(crumb: Crumb): Int = Font.BOLD
    }.apply {
      setCrumbs(listOf(Crumb.Impl(null, "Plugins", null, emptyList<Action>())))
    }
  }

  private fun fixedComponent(width: Int, height: Int): JPanel {
    return JPanel().apply {
      preferredSize = Dimension(JBUI.scale(width), JBUI.scale(height))
      minimumSize = preferredSize
    }
  }

  private fun layoutAtPreferredSize(component: JComponent, extraWidth: Int = 0) {
    component.size = Dimension(component.preferredSize.width + JBUI.scale(extraWidth), component.preferredSize.height)
    layoutRecursively(component)
  }

  private fun layoutRecursively(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layoutRecursively)
  }

  private fun assertSameVerticalCenter(ancestor: JComponent, reference: Component, vararg components: Component) {
    val expected = verticalCenterTwice(reference, ancestor)
    for (component in components) {
      assertThat(abs(verticalCenterTwice(component, ancestor) - expected))
        .describedAs("vertical center of %s", component.javaClass.simpleName)
        .isLessThanOrEqualTo(2)
    }
  }

  private fun assertActionGap(ancestor: JComponent, search: Component, action: Component) {
    val searchRightX = SwingUtilities.convertPoint(search, search.width, 0, ancestor).x
    val actionX = SwingUtilities.convertPoint(action, 0, 0, ancestor).x
    assertThat(actionX - searchRightX).isEqualTo(JBUI.scale(8))
  }

  private fun assertComponentGap(ancestor: JComponent, title: Component, search: Component, expected: Int) {
    val titleRightX = SwingUtilities.convertPoint(title, title.width, 0, ancestor).x
    val searchX = SwingUtilities.convertPoint(search, 0, 0, ancestor).x
    assertThat(searchX - titleRightX).isEqualTo(JBUI.scale(expected))
  }

  private fun verticalCenterTwice(component: Component, ancestor: JComponent): Int {
    return SwingUtilities.convertPoint(component, 0, 0, ancestor).y * 2 + component.height
  }

  private fun componentBaseline(component: Component, ancestor: JComponent): Int {
    val location = SwingUtilities.convertPoint(component, 0, 0, ancestor)
    return location.y + component.getBaseline(component.width, component.height)
  }
}
