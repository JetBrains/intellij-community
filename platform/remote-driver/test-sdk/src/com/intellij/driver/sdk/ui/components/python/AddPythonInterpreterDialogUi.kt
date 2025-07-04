package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.addPythonInterpreterDialog(func: AddPythonInterpreterDialogUi.() -> Unit = {}) =
  x(AddPythonInterpreterDialogUi::class.java) { byTitle("Add Python Interpreter") }.apply(func)

class AddPythonInterpreterDialogUi(data: ComponentData): UiComponent(data) {
  val typeSelector = x("//div[@text='Type:']/following-sibling:: *[@class='ComboBox'][1]")
  val okButton = x { byAccessibleName("OK") }
  val pathToPipenvField = x("//div[@text='Path to pipenv:']/following-sibling:: *[@class='TextFieldWithBrowseButton'][1]")
  val pathToPoetryField = x("//div[@text='Path to poetry:']/following-sibling:: *[@class='TextFieldWithBrowseButton'][1]")
  val useVenv1Link = x { byAccessibleName("Use .venv1") }
}