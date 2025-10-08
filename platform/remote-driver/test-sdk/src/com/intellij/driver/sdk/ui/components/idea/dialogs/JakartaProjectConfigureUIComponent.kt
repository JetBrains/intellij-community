package com.intellij.driver.sdk.ui.components.idea.dialogs

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui


fun NewProjectDialogUI.jakartaProjectConfigureUIComponent(action: JakartaProjectConfigureUIComponent.() -> Unit = {}): JakartaProjectConfigureUIComponent {
  return x("//div[@class='DialogRootPane']", JakartaProjectConfigureUIComponent::class.java).apply(action)
}

class JakartaProjectConfigureUIComponent(data: ComponentData) : UiComponent(data) {

  private fun openDropdown(dropdown: String) {
    x("//div[@accessiblename='$dropdown']/div[@class='BasicArrowButton']").click()
  }

  fun pickTemplate(template: String) {
    step("Pick Jakarta project template") {
      openDropdown("Template:")
      driver.ui.popup().accessibleList().clickItem(template, false)
    }
  }

  fun pickApplicationServer(applicationServer: String) {
    step("Pick Jakarta application server") {
      openDropdown("Application Server:")
      driver.ui.popup().accessibleList().clickItem(applicationServer, false)
    }
  }

  fun pickJakartaVersion(jakartaVersion: String) {
    step("Pick Jakarta version") {
      openDropdown("Version:")
      driver.ui.popup().accessibleList().clickItem(jakartaVersion, false)
    }
  }

  fun getActualAddedDependencies(): List<String> {
    val list: List<UiComponent> = xx("//div[@accessiblename='Added dependencies:']/following-sibling::div[@class='SelectedLibrariesPanel']//div[@class='ScrollablePanel']").list()
    if (list.isEmpty()) {
      x("//div[@emptytext='No dependencies added']").shouldBe { present() }
      return listOf()
    }
    else {
      return list[0].getAllTexts()
        .map { it.text }
        .toList()
    }
  }
}