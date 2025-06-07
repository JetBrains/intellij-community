package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.ui
import java.awt.Frame
import javax.swing.JFrame

fun Finder.ideFrame() = x(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  ideFrame().action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit = {}): IdeaFrameUI = ui.ideFrame().apply(action)

fun Finder.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl' and contains(@accessiblename, '${projectName}')]", IdeaFrameUI::class.java).action()
}

fun Driver.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  this.ui.projectIdeFrame(projectName, action)
}

open class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  private val ideaFrameComponent by lazy { driver.cast(component, IdeFrameImpl::class) }

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val isFullScreen: Boolean
    get() = ideaFrameComponent.isInFullScreen()

  val isMaximized: Boolean
    get() = ideaFrameComponent.getExtendedState().and(JFrame.MAXIMIZED_BOTH) != 0

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi = x(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }

  val rightToolWindowToolbar: ToolWindowRightToolbarUi = x(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }

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

  fun toFront() {
    ideaFrameComponent.toFront()
    mainToolbar.click()
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