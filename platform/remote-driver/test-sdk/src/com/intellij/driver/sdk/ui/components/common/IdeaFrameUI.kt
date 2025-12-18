package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.components.elements.WindowUiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import java.awt.Frame
import java.awt.Point
import javax.swing.JFrame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Finder.ideFrame() = x(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.ideFrames() = xx(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  ideFrame().action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrame().apply(action)

fun Driver.ideFrame(index: Int, action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrames().list()[index].apply(action)

fun Finder.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl' and contains(@accessiblename, '${projectName}')]", IdeaFrameUI::class.java).action()
}

fun Driver.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  this.ui.projectIdeFrame(projectName, action)
}

open class IdeaFrameUI(data: ComponentData) : WindowUiComponent(data) {
  private val ideaFrameComponent by lazy { driver.cast(component, IdeFrameImpl::class) }

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val isFullScreen: Boolean
    get() = ideaFrameComponent.isInFullScreen()

  val isMaximized: Boolean
    get() = ideaFrameComponent.getExtendedState().and(JFrame.MAXIMIZED_BOTH) != 0

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi = x(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }

  val rightToolWindowToolbar: ToolWindowRightToolbarUi = x(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }

  fun waitForIndicators(timeout: Duration = 5.minutes) {
    driver.waitForIndicators(::project, timeout)
  }

  fun waitForIndicatorsAndEnsureFocused(timeout: Duration = 5.minutes) {
    waitForIndicators(timeout)
    ensureFocused()
  }

  fun ensureFocused() {
    if (!isFocused() || !robot.hasInputFocus()) {
      toFront()
    }
  }

  fun closeProject() {
    step("Close project window and wait for it to disappear") {
      driver.invokeAction("CloseProject")
    }
  }

  fun maximize() = driver.withContext(OnDispatcher.EDT) {
    ideaFrameComponent.setExtendedState(ideaFrameComponent.getExtendedState().or(JFrame.MAXIMIZED_BOTH))
  }

  fun resize(width: Int, height: Int) = driver.withContext(OnDispatcher.EDT) {
    ideaFrameComponent.setSize(width, height)
  }

  fun openSettingsDialog() = driver.invokeAction("ShowSettings", now = false)

  override fun toFront() {
    super.toFront()
    click(Point(component.width / 2, 0))
  }

  fun isMinimized() = ideaFrameComponent.getState() == Frame.ICONIFIED

  fun unminimize() {
    ideaFrameComponent.setState(Frame.NORMAL)
  }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}

@Remote("com.intellij.openapi.wm.impl.IdeFrameImpl")
interface IdeFrameImpl : Window {
  fun isInFullScreen(): Boolean
  fun getExtendedState(): Int
  fun setExtendedState(state: Int)
  fun setSize(width: Int, height: Int)
  fun getState(): Int
  fun setState(state: Int)
}