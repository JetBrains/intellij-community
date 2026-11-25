package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.textField
import javax.swing.JTextField

/** The `New Go File` pop-up of the `Go.NewGoFile` action. */
fun Finder.newGoFilePopup(): NewGoFilePopupUi =
  x(NewGoFilePopupUi::class.java) { byClass("HeavyWeightWindow") and byAccessibleName("New Go File") }

class NewGoFilePopupUi(data: ComponentData) : PopupUiComponent(data) {
  /** The field of the file name. It goes by its empty text `Name`. */
  val nameField: JTextFieldUI = textField("New Go File name") { byType(JTextField::class.java) and byAttribute("myemptytext", "Name") }
}
