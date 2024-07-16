package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language


fun Finder.textField(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JTextField']", JTextFieldUI::class.java)

class JTextFieldUI(data: ComponentData) : UiComponent(data) {
  private val textFieldComponent by lazy { driver.cast(component, JTextField::class) }
  var text: String
    get() = textFieldComponent.getText()
    set(value) = driver.withContext(OnDispatcher.EDT) { textFieldComponent.setText (value) }
}

@Remote("javax.swing.JTextField")
interface JTextField {
  fun getText(): String
  fun setText(document: String)
}