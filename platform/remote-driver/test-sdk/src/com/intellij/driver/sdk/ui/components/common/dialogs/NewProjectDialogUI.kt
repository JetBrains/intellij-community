package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui

fun Finder.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  x("//div[@title='New Project']", NewProjectDialogUI::class.java).action()
}

fun Driver.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  this.ui.newProjectDialog(action)
}

open class NewProjectDialogUI(data: ComponentData) : UiComponent(data) {
  fun setProjectName(text: String) {
    nameTextField.doubleClick()
    keyboard {
      backspace()
      driver.ui.pasteText(text)
    }
  }

  fun chooseProjectType(projectType: String) {
    projectTypeList.waitOneText(projectType).click()
  }

  open fun chooseBuildSystem(buildSystem: String) {
    x("//div[@text='Build system:']/following-sibling::div[@class='SegmentedButtonComponent']")
      .waitOneText(buildSystem)
      .click()
  }

  open fun chooseGradleDsl(gradleDsl: String) {
    x("//div[@text='Gradle DSL:']/following-sibling::div[@class='SegmentedButtonComponent']")
      .waitOneText(gradleDsl)
      .click()
  }

  val nameTextField = textField("//div[@accessiblename='Name:' and @class='JBTextField']")
  val nextButton = x("//div[@text='Next']")
  open val createButton = x("//div[@text='Create']")
  private val projectTypeList = x("//div[@class='JBList']")
  val sampleCodeLabel = checkBox { byText("Add sample code") }
  val multiModuleLabel = checkBox { byText("Generate multi-module build") }
  val compactStructureLabel = checkBox { byText("Use compact project structure") }
  val createMainPyCheckbox = checkBox { byText("Create a welcome script") }
  val installMinicondaLink = x("//div[@text='Install Miniconda']")
}