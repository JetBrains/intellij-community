package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.waitForSmartMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Finder.ideFrame(action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl']", IdeaFrameUI::class.java).action()
}

open class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  val projectViewTree = tree("//div[@class='ProjectViewTree']")

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  fun dumbAware(timeout: Duration = 1.minutes, action: IdeaFrameUI.() -> Unit) {
    waitForSmartMode(timeout)
    action()
    waitForSmartMode(timeout)
  }

  private fun waitForSmartMode(timeout: Duration = 1.minutes) {
    driver.waitForSmartMode(project!!, timeout)
  }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}