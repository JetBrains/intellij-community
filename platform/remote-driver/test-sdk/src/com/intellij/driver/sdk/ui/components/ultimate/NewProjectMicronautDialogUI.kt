package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.ui

fun Finder.newProjectMicronautDialog(action: NewProjectMicronautDialogUI.() -> Unit) {
  x("//div[@title='New Project']", NewProjectMicronautDialogUI::class.java).action()
}

fun Driver.newProjectMicronautDialog(action: NewProjectMicronautDialogUI.() -> Unit) {
  this.ui.newProjectMicronautDialog(action)
}

class NewProjectMicronautDialogUI(data: ComponentData) : UiComponent(data) {
  fun setProjectName(text: String) {
    nameTextField.text = text
  }

  fun chooseLanguage(language: String) {
    x("//div[@visible_text='$language']")
      .click()
  }

  fun chooseBuildSystem(buildSystem: String) {
    x("//div[@visible_text='$buildSystem']")
      .waitOneText(buildSystem)
      .click()
  }

  fun chooseGenerator(name: String) {
    x("//div[@class='JBList']")
      .waitOneText(name)
      .click()
  }

  fun chooseTestFramework(name: String) {
    x("//div[@visible_text='$name']")
      .waitOneText(name)
      .click()
  }

  val nameTextField: JTextFieldUI = textField({and(byAccessibleName("Name:"), byClass("JBTextField"))})
  val nextButton: JButtonUiComponent = button("Next")
  val createButton: JButtonUiComponent = button("Create")
}