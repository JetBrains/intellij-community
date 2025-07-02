package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.addPythonInterpreterDialog(func: AddPythonInterpreterDialogUi.() -> Unit = {}) =
  x(AddPythonInterpreterDialogUi::class.java) { byTitle("Add Python Interpreter") }.apply(func)

class AddPythonInterpreterDialogUi(data: ComponentData): UiComponent(data) {
  val interpreterSelector = x("//div[@class='PythonInterpreterComboBox']")
  val typeSelector = x("//div[@text='Type:']/following-sibling:: *[@class='ComboBox'][1]")
  val okButton = x { byAccessibleName("OK") }
}