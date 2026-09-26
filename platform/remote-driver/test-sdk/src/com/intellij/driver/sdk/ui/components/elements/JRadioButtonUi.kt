package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JRadioButton

fun Finder.radioButton(locator: String = xQuery { byType(JRadioButton::class.java.name) }): JRadioButtonUi =
  x(locator, JRadioButtonUi::class.java)

fun Finder.radioButton(locator: QueryBuilder.() -> String): JRadioButtonUi = x(JRadioButtonUi::class.java) { locator()}

class JRadioButtonUi(data: ComponentData) : UiComponent(data) {
  private val radioButtonComponent get() = driver.cast(component, JRadioButtonComponent::class)

  val isSelected get() = radioButtonComponent.isSelected()
}

@Remote("javax.swing.JRadioButton")
interface JRadioButtonComponent {
  fun isSelected(): Boolean
}
