package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.tree
import org.intellij.lang.annotations.Language

class MavenPanelToolWindowUi(data: ComponentData) : UiComponent(data) {
  val reload: ActionButtonUi
    get() = driver.step("Search for 'Sync/Reload All Maven Projects' button") {
      actionButtonByXpath("//div[@myicon='refresh.svg']")
    }

  val tree: JTreeUiComponent
    get() = tree()
}

fun Finder.mavenPanel(@Language("xpath") xpath: String? = null, action: MavenPanelToolWindowUi.() -> Unit = {}) {
  driver.ideFrame {
    rightToolWindowToolbar.mavenButton.open()
  }
  x(xpath ?: "//div[@class='MavenProjectsNavigatorPanel']", MavenPanelToolWindowUi::class.java).action()
}