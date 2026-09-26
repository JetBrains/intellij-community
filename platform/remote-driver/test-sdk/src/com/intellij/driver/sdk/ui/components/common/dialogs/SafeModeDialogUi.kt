package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.button

fun Finder.safeModeDialogUi(action: SafeModeDialogUi.() -> Unit = {}): SafeModeDialogUi {
  return x(SafeModeDialogUi::class.java) { byClass("MyDialog") }.apply { action() }
}

class SafeModeDialogUi(data: ComponentData) : UiComponent(data) {
  val dontOpenButton: JButtonUiComponent = button { byAccessibleName("Don't Open") }
  val previewInSafeModeButton: JButtonUiComponent = button { byAccessibleName("Preview in Safe Mode") }
  val trustProjectButton: JButtonUiComponent = button { byAccessibleName("Trust Project") }
}