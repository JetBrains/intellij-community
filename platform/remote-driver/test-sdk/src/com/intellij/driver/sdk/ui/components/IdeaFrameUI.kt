package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.DumbService
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.*
import com.intellij.driver.sdk.ui.remote.Component
import java.time.Duration

fun Finder.idea(action: IdeaFrameUI.() -> Unit) {
  x("//div[@class='IdeFrameImpl']", IdeaFrameUI::class.java).action()
}

class IdeaFrameUI(data: ComponentData) : UiComponent(data) {
  val projectViewTree = tree("//div[@class='ProjectViewTree']")

  val project: Project?
    get() = driver.utility(ProjectFrameHelper::class).getFrameHelper(component).getProject()

  fun isDumbMode(): Boolean {
     return project?.let {     driver.service(DumbService::class, it).isDumb(it) } ?: true
  }

  fun dumbAware(timeout: Duration = Duration.ofMinutes(1), action: IdeaFrameUI.() -> Unit) {
    waitForSmartMode(timeout)
    action()
    waitForSmartMode(timeout)
  }

  fun waitForSmartMode(timeout: Duration = Duration.ofMinutes(1)) {
    waitFor(timeout, errorMessage = "Failed to wait ${timeout.seconds}s for smart mode") {
      isDumbMode().not()
    }
  }
}

@Remote("com.intellij.openapi.wm.impl.ProjectFrameHelper")
interface ProjectFrameHelper {
  fun getFrameHelper(window: Component): ProjectFrameHelper
  fun getProject(): Project?
}