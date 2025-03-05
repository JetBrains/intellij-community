package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.button(text: String) =
  x("//div[@class='JButton' and @visible_text='$text']", JButtonUiComponent::class.java)

fun Finder.button(locator: QueryBuilder.() -> String) = x(JButtonUiComponent::class.java) { locator() }

fun Finder.buttons(@Language("xpath") xpath: String? = null) =
  xx(xpath ?: "//div[@class='JButton']", JButtonUiComponent::class.java)

class JButtonUiComponent(data: ComponentData) : UiComponent(data) {

  private val button by lazy { driver.cast(component, JButtonRef::class) }

  val text
    get() = button.getText()
}

@Remote("javax.swing.JButton")
interface JButtonRef {
  fun getText(): String
}