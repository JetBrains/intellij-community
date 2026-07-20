package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import java.awt.Rectangle
import javax.swing.text.JTextComponent as SwingJTextComponent

fun Finder.textComponent(@Language("xpath") xpath: String? = null): JTextComponentUI =
  x(xpath ?: xQuery { byType(SwingJTextComponent::class.java) }, JTextComponentUI::class.java)

fun Finder.textComponent(init: QueryBuilder.() -> String): JTextComponentUI = x(JTextComponentUI::class.java, init)

open class JTextComponentUI(data: ComponentData) : UiComponent(data) {
  private val textComponent by lazy { driver.cast(component, JTextComponent::class) }

  var text: String
    get() = textComponent.getText()
    set(value) = driver.withContext(OnDispatcher.EDT) { textComponent.setText(value) }

  val selectedText: String get() = textComponent.getSelectedText()

  fun appendText(appendString: String) {
    text += appendString
  }

  fun clickText(targetText: String) {
    require(targetText.isNotEmpty()) { "Text to click must not be empty" }
    val location = driver.withContext(OnDispatcher.EDT) {
      val document = textComponent.getDocument()
      val documentText = textComponent.getText(0, document.getLength())
      val position = documentText.indexOf(targetText)
      check(position >= 0) { "Cannot find text '$targetText' in text component" }
      @Suppress("DEPRECATION")
      val bounds = checkNotNull(textComponent.modelToView(position)) {
        "Cannot determine location of text '$targetText' in text component"
      }
      bounds.location.apply { translate(1, 1) }
    }
    click(location)
  }
}

@Remote("javax.swing.text.JTextComponent")
interface JTextComponent {
  fun getText(): String
  fun getText(offset: Int, length: Int): String
  fun getSelectedText(): String
  fun setText(document: String)
  fun getDocument(): JTextDocument
  fun modelToView(position: Int): Rectangle?
}

@Remote("javax.swing.text.Document")
interface JTextDocument {
  fun getLength(): Int
}
