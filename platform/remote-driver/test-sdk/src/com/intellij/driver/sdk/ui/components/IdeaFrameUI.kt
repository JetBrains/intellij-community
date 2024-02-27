package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.ui

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl']", IdeaFrameUI::class.java).action()
}

fun Driver.ideFrame(action: IdeaFrameUI.() -> Unit) {
  this.ui.ideFrame(action)
}

open class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  val projectViewTree = tree("//div[@class='ProjectViewTree']")

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  val isFullScreen: Boolean
    get() = driver.cast(component, IdeFrameImpl::class).isInFullScreen()

  val leftToolWindowToolbar: ToolWindowLeftToolbarUi
    get() = x(Locators.byClass("ToolWindowLeftToolbar"), ToolWindowLeftToolbarUi::class.java)

  val rightToolWindowToolbar: ToolWindowRightToolbarUi
    get() = x(Locators.byClass("ToolWindowRightToolbar"), ToolWindowRightToolbarUi::class.java)
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}

@Remote("com.intellij.openapi.wm.impl.IdeFrameImpl")
interface IdeFrameImpl {
  fun isInFullScreen(): Boolean
}