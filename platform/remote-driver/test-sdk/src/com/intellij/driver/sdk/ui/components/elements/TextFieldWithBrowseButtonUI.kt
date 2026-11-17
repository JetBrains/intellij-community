package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JButton

fun Finder.textFieldWithBrowseButton(
  @Language("xpath") xpath: String? = null,
): TextFieldWithBrowseButtonUi =
  x(xpath ?: xQuery { byType("com.intellij.openapi.ui.TextFieldWithBrowseButton") },
    TextFieldWithBrowseButtonUi::class.java)

fun Finder.textFieldWithBrowseButton(
  init: QueryBuilder.() -> String,
): TextFieldWithBrowseButtonUi =
  x(TextFieldWithBrowseButtonUi::class.java, init)

class TextFieldWithBrowseButtonUi(data: ComponentData) : UiComponent(data) {
  private val innerTextField get() = textField("//div[@class='ExtendableTextField']")

  /**
   * The browse icon inside the field. The icon is a button, and its accessible name is the tooltip 'Browse… (⇧⏎)',
   * see `ComponentWithBrowseButton` and `TextFieldWithPopupHandlerUI.IconHolder`.
   */
  val browseButton: UiComponent = x("'Browse' button") { and(byType(JButton::class.java), contains(byAccessibleName("Browse"))) }

  var text: String
    get() = innerTextField.text
    set(value) { innerTextField.text = value }
}
