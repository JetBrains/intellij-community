// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.SwingUtilities

private const val FOOTER_ID = "Footer"
private const val OTHER_ID = "Other"
private const val BOTTOM_ID = "Bottom"

@TestApplication
class ToolWindowLeftToolbarFooterTest {
  private val project by projectFixture()

  @Test
  fun `a footer window has its button in the footer and another left window stays at the top`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = createManager(buttonManager, disposable)

    val footer = manager.registerToolWindow(task(FOOTER_ID, isInFooter = true), buttonManager)
    val other = manager.registerToolWindow(task(OTHER_ID, isInFooter = false), buttonManager)

    assertThat(footer.stripeButton).isNotNull()
    assertThat(buttonManager.left.footerStripe.getButtons()).containsExactly(footer.stripeButton)
    assertThat(buttonManager.left.topStripe.getButtons()).containsExactly(other.stripeButton)
    assertThat(buttonManager.left.getButtonFor(FOOTER_ID)).isSameAs(footer.stripeButton)
  }

  @Test
  fun `a footer window leaves the footer on the right, and it comes back on the left`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = createManager(buttonManager, disposable)
    val entry = manager.registerToolWindow(task(FOOTER_ID, isInFooter = true), buttonManager)

    moveStripeButton(entry, ToolWindowAnchor.RIGHT, buttonManager)
    assertThat(buttonManager.left.footerStripe.getButtons()).isEmpty()
    assertThat(buttonManager.right.topStripe.getButtons()).containsExactly(entry.stripeButton)

    moveStripeButton(entry, ToolWindowAnchor.LEFT, buttonManager)
    assertThat(buttonManager.right.topStripe.getButtons()).isEmpty()
    assertThat(buttonManager.left.topStripe.getButtons()).isEmpty()
    assertThat(buttonManager.left.footerStripe.getButtons()).containsExactly(entry.stripeButton)
  }

  @Test
  fun `the footer button is below the buttons of the bottom windows`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = createManager(buttonManager, disposable)
    val footer = manager.registerToolWindow(task(FOOTER_ID, isInFooter = true), buttonManager)
    val bottom = manager.registerToolWindow(task(BOTTOM_ID, isInFooter = false, anchor = ToolWindowAnchor.BOTTOM), buttonManager)
    val toolbar = buttonManager.left
    toolbar.setSize(JBUI.scale(40), JBUI.scale(600))
    layOut(toolbar)

    val footerBounds = boundsInToolbar(footer.stripeButton!!.getComponent(), toolbar)
    val bottomBounds = boundsInToolbar(bottom.stripeButton!!.getComponent(), toolbar)
    assertThat(buttonManager.left.bottomStripe.getButtons()).containsExactly(bottom.stripeButton)
    assertThat(bottomBounds.y).isGreaterThan(toolbar.height / 2)
    assertThat(footerBounds.y).isGreaterThanOrEqualTo(bottomBounds.y + bottomBounds.height)
    assertThat(footerBounds.y + footerBounds.height).isLessThanOrEqualTo(toolbar.height)
  }

  private fun layOut(component: Component) {
    if (component is Container) {
      component.doLayout()
      component.components.forEach(::layOut)
    }
  }

  private fun boundsInToolbar(component: Component, toolbar: Component): Rectangle {
    return SwingUtilities.convertRectangle(component.parent, component.bounds, toolbar)
  }

  private fun createManager(buttonManager: ToolWindowPaneNewButtonManager, disposable: Disposable): ToolWindowManagerImpl {
    val manager = object : ToolWindowManagerImpl(project, isNewUi = true, isEdtRequired = false, (project as ComponentManagerEx).getCoroutineScope()) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    // the layout gives the buttons: a test window has no plugin, so the New UI counts it as a bundled one without a button
    val layout = DesktopLayout()
    val anchors = mapOf(FOOTER_ID to ToolWindowAnchor.LEFT, OTHER_ID to ToolWindowAnchor.LEFT, BOTTOM_ID to ToolWindowAnchor.BOTTOM)
    for ((order, entry) in anchors.entries.withIndex()) {
      layout.addInfo(entry.key, WindowInfoImpl().also {
        it.id = entry.key
        it.anchor = entry.value
        it.order = order
      })
    }
    manager.setLayoutOnInit(layout)
    return manager
  }

  private fun task(id: String, isInFooter: Boolean, anchor: ToolWindowAnchor = ToolWindowAnchor.LEFT): RegisterToolWindowTaskData {
    return RegisterToolWindowTaskData(id = id, anchor = anchor, contentFactory = object : ToolWindowFactory {
      override fun init(toolWindow: ToolWindow) {
        (toolWindow as ToolWindowImpl).isInLeftToolbarFooter = isInFooter
      }

      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      }
    })
  }

  /** Moves the stripe button as [ToolWindowManagerImpl.setToolWindowAnchor] does, but without a frame. */
  private fun moveStripeButton(entry: ToolWindowEntry, anchor: ToolWindowAnchor, buttonManager: ToolWindowPaneNewButtonManager) {
    val info = entry.readOnlyWindowInfo
    entry.removeStripeButton(info.anchor, info.isSplit)
    entry.toolWindow.applyWindowInfo((info as WindowInfoImpl).copy().also { it.anchor = anchor })
    entry.stripeButton = buttonManager.createStripeButton(entry.toolWindow, entry.readOnlyWindowInfo, task = null)
  }
}
