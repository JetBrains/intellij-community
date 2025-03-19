package com.intellij.driver.sdk.ui

import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.openapi.diagnostic.logger
import java.awt.Point

class UiText(private val component: UiComponent, private val textData: TextData) {
  companion object {
    private val LOG get() = logger<UiText>()

    fun List<UiText>.asString(separator: String = "") = joinToString(separator) { it.text }
  }

  val text get(): String = textData.text
  val point get(): Point = textData.point
  val bundleKey get(): String = textData.bundleKey

  override fun toString(): String {
    return text
  }

  fun click() {
    component.click(textData.point)
  }

  fun doubleClick() {
    component.doubleClick(textData.point)
  }

  fun rightClick() {
    component.rightClick(textData.point)
  }

  fun moveMouse() {
    component.moveMouse(textData.point)
  }
}