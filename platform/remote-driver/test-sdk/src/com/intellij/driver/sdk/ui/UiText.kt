package com.intellij.driver.sdk.ui

import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.openapi.diagnostic.logger

class UiText(private val component: UiComponent, private val textData: TextData) {
  companion object {
    private val LOG get() = logger<UiText>()

    fun List<UiText>.allText(separator: String = "") = joinToString(separator) { it.text }
  }

  val text = textData.text
  val point = textData.point
  val bundleKey = textData.bundleKey

  override fun toString(): String {
    return "UiText[point=${point},text=$text]"
  }

  fun click() {
    LOG.info("Click at '${text}'")
    component.click(textData.point)
  }

  fun doubleClick() {
    LOG.info("Double click at '${text}'")
    component.doubleClick(textData.point)
  }

  fun rightClick() {
    LOG.info("Right click at '${text}'")
    component.rightClick(textData.point)
  }

  fun moveMouse() {
    LOG.info("Move mouse to ${textData.point}")
    component.moveMouse(textData.point)
  }
}