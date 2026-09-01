package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MavenPanelToolWindowUi(data: ComponentData) : UiComponent(data) {
  val reload: ActionButtonUi
    get() = driver.step("Search for 'Sync/Reload All Maven Projects' button") {
      actionButtonByXpath("//div[@myicon='refresh.svg']")
    }

  val tree: JTreeUiComponent
    get() = tree()

  fun importMavenProject() {
    driver.invokeAction("Maven.Reimport")
  }

  fun debugTask(vararg pathToTask: String) {
    executeTask(pathToTask = pathToTask, taskName = "Debug '")
  }

  fun runTask(vararg pathToTask: String) {
    executeTask(pathToTask = pathToTask, taskName = "Run '")
  }

  fun toggleSkipTestsMode() {
    driver.invokeAction("Maven.ToggleSkipTests")
  }

  private fun executeTask(taskName: String, vararg pathToTask: String) {
    tree.run {
      waitForNodesLoaded(1.minutes)
      rightClickPath(*pathToTask, fullMatch = false)
    }
    driver.ui.popupMenu().waitAnyTextsContains(timeout = 10.seconds, text = taskName).single().click()
  }
}

fun Finder.mavenPanel(@Language("xpath") xpath: String? = null, action: MavenPanelToolWindowUi.() -> Unit = {}) {
  driver.ideFrame {
    rightToolWindowToolbar.mavenButton.open()
  }
  x(xpath ?: "//div[@class='MavenProjectsNavigatorPanel']", MavenPanelToolWindowUi::class.java).action()
}