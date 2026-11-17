package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import javax.swing.JSlider

fun Finder.slider(readableName: String? = null, locator: QueryBuilder.() -> String = { byType(JSlider::class.java) }): JSliderUi =
  x(JSliderUi::class.java, readableName, locator)

class JSliderUi(data: ComponentData) : UiComponent(data) {
  private val sliderComponent get() = driver.cast(component, JSliderComponent::class)

  val value: Int get() = sliderComponent.getValue()
  val minimum: Int get() = sliderComponent.getMinimum()
  val maximum: Int get() = sliderComponent.getMaximum()
}

@Remote("javax.swing.JSlider")
private interface JSliderComponent {
  fun getValue(): Int
  fun getMinimum(): Int
  fun getMaximum(): Int
}
