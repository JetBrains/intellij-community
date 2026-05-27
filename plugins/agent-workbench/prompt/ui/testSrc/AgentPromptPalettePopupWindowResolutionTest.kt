// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.BalloonLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.condition.DisabledIf
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel

@DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless", disabledReason = "Test is disabled in headless environment")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPalettePopupWindowResolutionTest {
  @Test
  fun mouseClickInSameIdeFrameAllowsPopupCancellation() {
    val project = projectProxy("source-project")
    val frame = TestIdeFrame(project)
    try {
      val content = frame.rootComponent()

      assertThat(
        shouldAllowPromptPopupCancellation(
          popupProject = project,
          isRecentSourceFrameActivation = false,
          currentEvent = mousePressed(content),
          isExplicitClose = false,
          resolveProject = ::resolveProjectForComponent,
        )
      ).isTrue()
    }
    finally {
      frame.dispose()
    }
  }

  @Test
  fun mouseClickInOtherIdeFrameDoesNotAllowPopupCancellation() {
    val sourceProject = projectProxy("source-project")
    val sourceFrame = TestIdeFrame(sourceProject)
    val otherProject = projectProxy("other-project")
    val otherFrame = TestIdeFrame(otherProject)
    try {
      val otherContent = otherFrame.rootComponent()

      assertThat(
        shouldAllowPromptPopupCancellation(
          popupProject = sourceProject,
          isRecentSourceFrameActivation = false,
          currentEvent = mousePressed(otherContent),
          isExplicitClose = false,
          resolveProject = ::resolveProjectForComponent,
        )
      ).isFalse()
    }
    finally {
      otherFrame.dispose()
      sourceFrame.dispose()
    }
  }

  @Test
  fun mouseClickInOwnedChildWindowOfSameFrameAllowsPopupCancellation() {
    val project = projectProxy("source-project")
    val frame = TestIdeFrame(project)
    val ownedDialog = JDialog(frame)
    try {
      val content = JPanel()
      ownedDialog.contentPane.add(content)

      assertThat(
        shouldAllowPromptPopupCancellation(
          popupProject = project,
          isRecentSourceFrameActivation = false,
          currentEvent = mousePressed(content),
          isExplicitClose = false,
          resolveProject = ::resolveProjectForComponent,
        )
      ).isTrue()
    }
    finally {
      ownedDialog.dispose()
      frame.dispose()
    }
  }

  @Test
  fun switchingToAnotherAppDoesNotAllowPopupCancellation() {
    val project = projectProxy("source-project")
    val frame = TestIdeFrame(project)
    try {
      assertThat(
        shouldAllowPromptPopupCancellation(
          popupProject = project,
          isRecentSourceFrameActivation = false,
          currentEvent = WindowEvent(frame, WindowEvent.WINDOW_DEACTIVATED),
          isExplicitClose = false,
          resolveProject = ::resolveProjectForComponent,
        )
      ).isFalse()
    }
    finally {
      frame.dispose()
    }
  }

  private fun TestIdeFrame.rootComponent(): Component {
    val panel = JPanel()
    contentPane.add(panel)
    return panel
  }

  private fun mousePressed(source: Component): MouseEvent {
    return MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, false)
  }

  private fun projectProxy(name: String): Project {
    val handler = InvocationHandler { proxy, method, args ->
      when (method.name) {
        "getName" -> name
        "isOpen" -> true
        "isDisposed" -> false
        "toString" -> "MockProject($name)"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
      }
    }
    return Proxy.newProxyInstance(
      Project::class.java.classLoader,
      arrayOf(Project::class.java),
      handler,
    ) as Project
  }

  private class TestIdeFrame(private val frameProject: Project) : JFrame(), IdeFrame {
    override fun getStatusBar(): StatusBar? = null

    override fun suggestChildFrameBounds(): Rectangle = Rectangle()

    override fun getProject(): Project = frameProject

    override fun setFrameTitle(title: String) = Unit

    override fun getComponent(): JComponent = rootPane

    override fun getBalloonLayout(): BalloonLayout? = null
  }
}
