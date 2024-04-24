package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.ui
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent
import javax.swing.JFrame

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl']", IdeaFrameUI::class.java).action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit) {
  this.ui.ideFrame(action)
}

open class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  private val projectViewTreeClass = if (isRemoteIdeMode) "ThinClientProjectViewTree" else "ProjectViewTree"
  val projectViewTree = tree("//div[@class='${projectViewTreeClass}']")

  private val ideaFrameComponent by lazy { driver.cast(component, IdeFrameImpl::class) }

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val isFullScreen: Boolean
    get() = ideaFrameComponent.isInFullScreen()

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi = x(Locators.byClass("ToolWindowLeftToolbar"), ToolWindowLeftToolbarUi::class.java)

  val rightToolWindowToolbar: ToolWindowRightToolbarUi = x(Locators.byClass("ToolWindowRightToolbar"), ToolWindowRightToolbarUi::class.java)

  val settingsButton = x("//div[@myicon='settings.svg']")

  val runActionButton = x("//div[@myicon='run.svg']")

  fun maximize() = driver.withContext(OnDispatcher.EDT) {
    ideaFrameComponent.setExtendedState(ideaFrameComponent.getExtendedState().or(JFrame.MAXIMIZED_BOTH))
  }

  fun openSettingsDialog() = if (SystemInfo.isMac)
    keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA) }
  else
    keyboard { hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_S) }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}

@Remote("com.intellij.openapi.wm.impl.IdeFrameImpl")
interface IdeFrameImpl {
  fun isInFullScreen(): Boolean
  fun getExtendedState(): Int
  fun setExtendedState(state: Int)
}