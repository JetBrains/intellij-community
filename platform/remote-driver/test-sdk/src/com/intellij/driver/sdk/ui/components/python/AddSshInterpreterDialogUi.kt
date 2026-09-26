package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.textField

fun IdeaFrameUI.addSshInterpreterDialog(func: AddSshInterpreterDialogUi.() -> Unit = {}) =
  x(AddSshInterpreterDialogUi::class.java, "'New Target: SSH' wizard") { byTitle("New Target: SSH") }.apply(func)

class AddSshInterpreterDialogUi(data: ComponentData) : UiComponent(data) {

  val newConnectionRadioButton = x("'New' SSH connection radio button") {
    and(byAccessibleName("New"), byClass("JBRadioButton"))
  }
  val hostField = textField("'Host:' field") { and(byAccessibleName("Host:"), byClass("JBTextField")) }
  val portField = textField("'Port:' field") { and(byAccessibleName("Port:"), byClass("JBTextField")) }
  val usernameField = textField("'Username:' field") { and(byAccessibleName("Username:"), byClass("JBTextField")) }
  val passwordField = x("(//div[@class='JBPasswordField'])[1]", JTextFieldUI::class.java, "'Password:' field")
  val selectExistingRadioButton = x("'Select existing' environment radio button") {
    and(byAccessibleName("Select existing"), byClass("JBRadioButton"))
  }
  val pythonPathComboBox = x("'Python path:' combo box") { byClass("PythonInterpreterComboBox") }
  val nextButton = x("'Next' button") { byAccessibleName("Next") }
  val createButton = x("'Create' button") { byAccessibleName("Create") }
}
