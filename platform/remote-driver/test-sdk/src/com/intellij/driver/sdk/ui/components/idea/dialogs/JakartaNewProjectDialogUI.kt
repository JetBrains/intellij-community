package com.intellij.driver.sdk.ui.components.idea.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JComboBox

fun Driver.jakartaNewProjectDialog(action: JakartaNewProjectDialogUI.() -> Unit) {
  this.ui.x(xQuery { byTitle("New Project") }, JakartaNewProjectDialogUI::class.java).action()
}

class JakartaNewProjectDialogUI(data: ComponentData) : NewProjectDialogUI(data) {

  private fun pickDropdownByAccessibleName(dropdownAccessibleName: String): JComboBoxUiComponent {
    return comboBox {
      and(byAccessibleName(dropdownAccessibleName), byType(JComboBox::class.java))
    }
  }

  fun pickTemplate(template: String) {
    step("Pick Jakarta project template") {
      pickDropdownByAccessibleName("Template:").selectItemContains(template)
    }
  }

  fun pickApplicationServer(applicationServer: String) {
    step("Pick Jakarta application server") {
      pickDropdownByAccessibleName("Application Server:").selectItemContains(applicationServer)
    }
  }

  fun pickJakartaVersion(jakartaVersion: String) {
    step("Pick Jakarta version") {
      step("Pick Jakarta version") {
        pickDropdownByAccessibleName("Version:").selectItemContains(jakartaVersion)
      }
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