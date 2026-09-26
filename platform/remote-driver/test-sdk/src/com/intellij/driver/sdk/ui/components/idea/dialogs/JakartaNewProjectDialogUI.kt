package com.intellij.driver.sdk.ui.components.idea.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBoxTree
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

  /**
   * Checks a specification in the "Specifications" category of the dependencies tree.
   *
   * @param profileTitle the library title, for example "Full Platform", "Web Profile" or "Core Profile"
   */
  fun pickSpecificationProfile(profileTitle: String) {
    step("Pick the '$profileTitle' specification") {
      checkBoxTree().apply {
        expandPath(SPECIFICATIONS_CATEGORY)
        // The renderer appends the version to the title, so the node reads "Full Platform (11.0.0)".
        val nodePath = collectCheckboxes()
          .map { it.path }
          .firstOrNull { it.size == 2 && it[0] == SPECIFICATIONS_CATEGORY && it[1].startsWith(profileTitle) }
          ?: error("No '$profileTitle' node under '$SPECIFICATIONS_CATEGORY' in the dependencies tree")
        switchCheckBoxByPath(nodePath, true)
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

  private companion object {
    const val SPECIFICATIONS_CATEGORY = "Specifications"
  }
}