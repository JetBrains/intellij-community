package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.pythonInterpretersDialog(func: PythonInterpretersDialogUi.() -> Unit = {}) =
  x(PythonInterpretersDialogUi::class.java) { byTitle("Python Interpreters") }.apply(func)

class PythonInterpretersDialogUi(data: ComponentData): UiComponent(data) {
  val renameButton = x { byAccessibleName("Rename") }
  val removeButton = x { byAccessibleName("Remove Interpreter") }
  val okButton = x { byText("OK") }
}