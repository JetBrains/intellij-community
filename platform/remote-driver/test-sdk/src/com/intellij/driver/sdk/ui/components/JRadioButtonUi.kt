package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import javax.swing.JRadioButton

fun Finder.radioButton(locator: String = Locators.byType(JRadioButton::class.java)): JRadioButtonUi = x(locator, JRadioButtonUi::class.java)

class JRadioButtonUi(data: ComponentData) : UiComponent(data) {
  private val radioButtonComponent get() = driver.cast(component, JRadioButtonComponent::class)

  val isSelected get() = radioButtonComponent.isSelected()
}

@Remote("javax.swing.JRadioButton")
interface JRadioButtonComponent {
  fun isSelected(): Boolean
}
