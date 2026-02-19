package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.tree
import org.intellij.lang.annotations.Language

class GradlePanelToolWindowUi(data: ComponentData) : UiComponent(data) {

  val reimport: ActionButtonUi
    get() = driver.step("Search for 'Reimport All Gradle Projects' button") {
      actionButtonByXpath("//div[@myicon='refresh.svg']")
    }

  val tree: JTreeUiComponent
    get() = tree()
}

fun IdeaFrameUI.gradlePanel(@Language("xpath") xpath: String? = null, action: GradlePanelToolWindowUi.() -> Unit = {}) {
  driver.ideFrame {
    rightToolWindowToolbar.gradleButton.open()
  }
  x(xpath ?: "//div[@class='ExternalProjectsViewImpl']", GradlePanelToolWindowUi::class.java).action()
}