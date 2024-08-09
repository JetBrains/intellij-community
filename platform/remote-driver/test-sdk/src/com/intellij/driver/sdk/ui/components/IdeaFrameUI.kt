package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.ui
import javax.swing.JFrame

fun Finder.ideFrame() = x(IdeaFrameUI::class.java) { byClass("IdeFrameImpl") }

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  ideFrame().action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit) {
  this.ui.ideFrame(action)
}

fun Finder.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl' and contains(@accessiblename, '${projectName}')]", IdeaFrameUI::class.java).action()
}

fun Driver.projectIdeFrame(projectName: String, action: IdeaFrameUI.() -> Unit) {
  this.ui.projectIdeFrame(projectName, action)
}

open class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  private val projectViewTreeClass = if (isRemoteIdeMode) "ThinClientProjectViewTree" else "ProjectViewTree"
  val projectViewTree = tree("//div[@class='${projectViewTreeClass}']")

  private val ideaFrameComponent by lazy { driver.cast(component, IdeFrameImpl::class) }

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val isFullScreen: Boolean
    get() = ideaFrameComponent.isInFullScreen()

  val isMaximized: Boolean
    get() = ideaFrameComponent.getExtendedState().and(JFrame.MAXIMIZED_BOTH) != 0

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi = x(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }

  val rightToolWindowToolbar: ToolWindowRightToolbarUi = x(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }

  val settingsButton = x("//div[@myicon='settings.svg']")

  val runActionButton = x("//div[@myicon='run.svg']")

  fun maximize() = driver.withContext(OnDispatcher.EDT) {
    ideaFrameComponent.setExtendedState(ideaFrameComponent.getExtendedState().or(JFrame.MAXIMIZED_BOTH))
  }

  fun resize(width: Int, height: Int) = driver.withContext(OnDispatcher.EDT) {
    ideaFrameComponent.setSize(width, height)
  }

  fun openSettingsDialog() = driver.invokeAction("ShowSettings", now = false)

  fun requestFocus() {
    ideaFrameComponent.requestFocus()
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
}