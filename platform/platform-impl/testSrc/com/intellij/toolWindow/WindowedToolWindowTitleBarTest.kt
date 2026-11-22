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
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.windowedTitleBarContent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.mac.macTitleBarGeometry
import com.intellij.ui.mac.macWindowButtonsGap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Container
import java.awt.event.MouseAdapter
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val TOOL_WINDOW_ID = "Windowed"

// the first test loads the classes of the tool window and its content
private val TIMEOUT = 60.seconds

@TestApplication
class WindowedToolWindowTitleBarTest {
  private val project by projectFixture()

  @Test
  fun `the empty space of the header, the tab panel and the name label move the window`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) {
    val decorator = createDecorator(disposable, contentCount = 2)
    val header = decorator.header
    val emptySideComponent = JPanel()
    header.sideComponent = emptySideComponent
    val westPanel = header.getComponent(0) as Container
    val tabPanel = westPanel.getComponent(0) as Container
    val nameLabel = tabPanel.components.single { it !is ContentTabLabel && it is JLabel }

    assertThat(header.isTitleBarArea(header)).isTrue()
    assertThat(header.isTitleBarArea(westPanel)).isTrue()
    assertThat(header.isTitleBarArea(tabPanel)).isTrue()
    assertThat(header.isTitleBarArea(nameLabel)).isTrue()
    assertThat(header.isTitleBarArea(emptySideComponent)).isTrue()
    // the parents of the header lie above it, in the gap of an island
    assertThat(header.isTitleBarArea(decorator)).isTrue()
  }

  @Test
  fun `a tab and a component with a mouse listener take the press`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) {
    val decorator = createDecorator(disposable, contentCount = 2)
    val header = decorator.header
    val clickableSideComponent = JLabel("side").also { it.addMouseListener(object : MouseAdapter() {}) }
    header.sideComponent = clickableSideComponent
    val tabPanel = (header.getComponent(0) as Container).getComponent(0) as Container
    val tabs = tabPanel.components.filterIsInstance<ContentTabLabel>()

    assertThat(tabs).hasSize(2)
    assertThat(tabs).allSatisfy { assertThat(header.isTitleBarArea(it)).isFalse() }
    assertThat(header.isTitleBarArea(clickableSideComponent)).isFalse()
    assertThat(header.isTitleBarArea(JPanel())).isFalse()
  }

  @Test
  fun `a visible header of a single tool window goes into the title bar row`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) {
    val decorator = createDecorator(disposable, contentCount = 1)
    val header = decorator.header

    val content = windowedTitleBarContent(decorator)

    assertThat(content).isNotNull()
    assertThat(content!!.isTitleBarArea(header)).isTrue()
  }

  @Test
  fun `a hidden header keeps the system title bar row`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) {
    val decorator = createDecorator(disposable, contentCount = 1)

    decorator.setHeaderVisible(false)

    assertThat(windowedTitleBarContent(decorator)).isNull()
  }

  @Test
  fun `a split tool window keeps the system title bar row`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) {
    val decorator = createDecorator(disposable, contentCount = 2)
    val second = decorator.contentManager.contents[1]

    decorator.splitWithContent(second, SwingConstants.RIGHT, -1)

    assertThat(decorator.mode).isEqualTo(InternalDecoratorImpl.Mode.HORIZONTAL_SPLIT)
    assertThat(windowedTitleBarContent(decorator)).isNull()
  }

  @Test
  fun `the window buttons take the gap of the project frame, and full screen without new controls takes a small gap`() {
    val gap = 26 + JBValue.Float(52f, true).unscaled.roundToInt()

    assertThat(macWindowButtonsGap(isFullScreen = false, newFullScreenControls = false)).isEqualTo(gap)
    assertThat(macWindowButtonsGap(isFullScreen = false, newFullScreenControls = true)).isEqualTo(gap)
    assertThat(macWindowButtonsGap(isFullScreen = true, newFullScreenControls = true)).isEqualTo(gap)
    assertThat(macWindowButtonsGap(isFullScreen = true, newFullScreenControls = false)).isEqualTo(5)
  }

  @Test
  fun `the leading inset is the inset that the runtime reports, and the gap of the window buttons otherwise`() {
    val reported = macTitleBarGeometry(height = 40f, leftInset = 76.5f, isFullScreen = false, newFullScreenControls = true)
    assertThat(reported.height).isEqualTo(40)
    assertThat(reported.leadingInset).isEqualTo(77)
    assertThat(reported.isFullScreen).isFalse()

    val notReported = macTitleBarGeometry(height = 40f, leftInset = 0f, isFullScreen = false, newFullScreenControls = true)
    assertThat(notReported.leadingInset).isEqualTo(JBUI.scale(macWindowButtonsGap(isFullScreen = false, newFullScreenControls = true)))

    val fullScreen = macTitleBarGeometry(height = 40f, leftInset = 76f, isFullScreen = true, newFullScreenControls = false)
    assertThat(fullScreen.leadingInset).isEqualTo(JBUI.scale(5))
    assertThat(fullScreen.isFullScreen).isTrue()
  }

  private fun createDecorator(disposable: Disposable, contentCount: Int): InternalDecoratorImpl {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = object : ToolWindowManagerImpl(project, isNewUi = true, isEdtRequired = false, (project as ComponentManagerEx).getCoroutineScope()) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    val layout = DesktopLayout()
    layout.addInfo(TOOL_WINDOW_ID, WindowInfoImpl().also {
      it.id = TOOL_WINDOW_ID
      it.anchor = ToolWindowAnchor.RIGHT
    })
    manager.setLayoutOnInit(layout)

    val task = RegisterToolWindowTaskData(id = TOOL_WINDOW_ID, anchor = ToolWindowAnchor.RIGHT, contentFactory = object : ToolWindowFactory {
      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      }
    })
    val toolWindow = manager.registerToolWindow(task, buttonManager).toolWindow
    val decorator = toolWindow.getOrCreateDecoratorComponent()
    val contentManager = toolWindow.contentManager
    repeat(contentCount) { index ->
      contentManager.addContent(ContentFactory.getInstance().createContent(JPanel(), "Content $index", false))
    }
    return decorator
  }
}
