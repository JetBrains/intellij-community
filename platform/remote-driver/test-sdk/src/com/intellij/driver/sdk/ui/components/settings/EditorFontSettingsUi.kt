package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.textField
import javax.swing.JTextField

/**
 * Settings | Editor | Font page.
 */
fun SettingsDialogUiComponent.editorFontSettingsPage(action: EditorFontSettingsPageUi.() -> Unit = {}): EditorFontSettingsPageUi =
  x(EditorFontSettingsPageUi::class.java, "Editor font settings page") {
    byType("com.intellij.application.options.editor.fonts.AppEditorFontOptionsPanel")
  }.apply(action)

class EditorFontSettingsPageUi(data: ComponentData) : UiComponent(data) {
  val sizeField: JTextFieldUI = fieldLabeledBy("Size")
  val lineHeightField: JTextFieldUI = fieldLabeledBy("Line height")

  /**
   * The fields of the page have no accessible name of their own, they inherit it from the label
   * bound with `JLabel.setLabelFor`, so the trailing colon of the label may or may not be a part of it.
   */
  private fun fieldLabeledBy(label: String): JTextFieldUI =
    textField("'$label' field") {
      and(byType(JTextField::class.java), or(byAccessibleName(label), byAccessibleName("$label:")))
    }
}
