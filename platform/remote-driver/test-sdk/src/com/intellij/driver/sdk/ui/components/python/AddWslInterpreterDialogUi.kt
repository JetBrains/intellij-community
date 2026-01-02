package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.addWslInterpreterDialog(func: AddWslInterpreterDialogUi.() -> Unit = {}) =
  x(AddWslInterpreterDialogUi::class.java) { byTitle("New Target: WSL") }.apply(func)

class AddWslInterpreterDialogUi(data: ComponentData): UiComponent(data) {
  val nextButton = x { byAccessibleName("Next") }
}