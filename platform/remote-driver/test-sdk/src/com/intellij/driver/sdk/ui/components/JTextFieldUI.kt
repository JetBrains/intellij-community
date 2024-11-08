package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language


fun Finder.textField(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(javax.swing.JTextField::class.java) }, JTextFieldUI::class.java)

fun Finder.textField(init: QueryBuilder.() -> String) = x(JTextFieldUI::class.java, init)

class JTextFieldUI(data: ComponentData) : UiComponent(data) {
  private val textFieldComponent by lazy { driver.cast(component, JTextComponent::class) }
  var text: String
    get() = textFieldComponent.getText()
    set(value) = driver.withContext(OnDispatcher.EDT) { textFieldComponent.setText(value) }

  val selectedText: String get() = textFieldComponent.getSelectedText()

  fun appendText(appendString: String) {
    text += appendString
  }
}

@Remote("javax.swing.text.JTextComponent")
interface JTextComponent {
  fun getText(): String
  fun getSelectedText(): String
  fun setText(document: String)
}