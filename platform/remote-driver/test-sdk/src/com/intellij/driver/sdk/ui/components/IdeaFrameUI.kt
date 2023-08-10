package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.waitForSmartMode
import java.time.Duration

fun Finder.idea(action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl']", IdeaFrameUI::class.java).action()
}

class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  val projectViewTree = tree("//div[@class='ProjectViewTree']")

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  fun dumbAware(timeout: Duration = Duration.ofMinutes(1), action: IdeaFrameUI.() -> Unit) {
    waitForSmartMode(timeout)
    action()
    waitForSmartMode(timeout)
  }

  fun waitForSmartMode(timeout: Duration = Duration.ofMinutes(1)) {
    driver.waitForSmartMode(project!!, timeout)
  }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}