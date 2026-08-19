package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

class GradlePanelToolWindowUi(data: ComponentData) : UiComponent(data) {

  val reimport: ActionButtonUi
    get() = driver.step("Search for 'Reimport All Gradle Projects' button") {
      actionButtonByXpath("//div[@myicon='refresh.svg']")
    }

  val tree: JTreeUiComponent get() = tree()

  fun debugTask(vararg pathToTask: String) {
    tree.run {
      rightClickPath(*pathToTask, fullMatch = false)
    }
    driver.ui.popupMenu()
      .waitAnyTextsContains(timeout = 10.seconds, text = "Debug '")
      .single()
      .click()
  }

  fun syncAllProjects() {
    driver.invokeAction("ExternalSystem.RefreshAllProjects")
  }

  fun analyzeDependencies() {
    driver.invokeAction("Gradle.ToolbarDependencyAnalyzer", component = component)
  }

  fun IdeaFrameUI.runTask(task: String) {
    driver.invokeAction("Gradle.ExecuteTask")
    popup().waitFound().textField { byClass("SearchField")}.text = task
    keyboard { enter() }
  }
}

fun IdeaFrameUI.gradlePanel(@Language("xpath") xpath: String? = null, action: GradlePanelToolWindowUi.() -> Unit = {}) {
  driver.ideFrame {
    rightToolWindowToolbar.gradleButton.open()
  }
  x(xpath ?: "//div[@class='ExternalProjectsViewImpl']", GradlePanelToolWindowUi::class.java).action()
}
