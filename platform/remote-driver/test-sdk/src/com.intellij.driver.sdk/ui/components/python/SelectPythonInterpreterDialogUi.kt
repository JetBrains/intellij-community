package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.selectPythonInterpreterDialog(func: SelectPythonInterpreterDialogUi.() -> Unit = {}) =
  x(SelectPythonInterpreterDialogUi::class.java) { byTitle("Select Python Interpreter") }.apply(func)

class SelectPythonInterpreterDialogUi(data: ComponentData) : UiComponent(data) {
  val comboBox = x { byClass("ComboBox") }
  val refreshButton = x { byAccessibleName("Refresh") }
  val okButton = x { byAccessibleName("OK") }
}
