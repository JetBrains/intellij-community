package com.intellij.driver.sdk.ui.components.idea

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent

fun Finder.projectStructure(action: ProjectStructureUI.() -> Unit) {
  x(ProjectStructureUI::class.java) { byTitle("Project Structure") }.action()
}

class ProjectStructureUI(data: ComponentData) : DialogUiComponent(data) {
  fun openSdkSettings() {
    x("//div[@class='JBList']")
      .waitOneText("SDKs")
      .click()
  }
}