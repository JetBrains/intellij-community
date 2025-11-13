package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.addPythonInterpreterDialog(func: AddPythonInterpreterDialogUi.() -> Unit = {}) =
  x(AddPythonInterpreterDialogUi::class.java) { byTitle("Add Python Interpreter") }.apply(func)

class AddPythonInterpreterDialogUi(data: ComponentData): UiComponent(data) {
  val typeSelector = x("//div[@text='Type:']/following-sibling:: *[@class='ComboBox'][1]")
  val pythonPath = x {byClass("PythonInterpreterComboBox")}
  val okButton = x { byAccessibleName("OK") }
  val useVenv1Link = x { byAccessibleName("Use .venv1") }
  val selectExisting = x { byAccessibleName("Select existing") }
  val generateNew = x { byAccessibleName("Generate new") }
  val browseButton = x("//div[@tooltiptext='Browse…']")

  fun clickPathToExecutable(type: String) = x("//div[@text='Path to $type:']/following-sibling:: *[@class='ValidatedPathField'][1]").click()
}