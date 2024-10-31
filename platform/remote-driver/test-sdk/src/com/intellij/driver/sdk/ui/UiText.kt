package com.intellij.driver.sdk.ui

import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.openapi.diagnostic.logger

class UiText(private val component: UiComponent, private val textData: TextData) {
  companion object {
    private val LOG get() = logger<UiText>()

    fun List<UiText>.allText(separator: String = "") = joinToString(separator) { it.text }
  }

  val text get() = textData.text
  val point get() = textData.point
  val bundleKey get() = textData.bundleKey

  override fun toString(): String {
    return "UiText[$text]"
  }

  fun click() {
    LOG.info("Click at '$this'")
    component.click(textData.point, silent = true)
  }

  fun doubleClick() {
    LOG.info("Double click at '$this'")
    component.doubleClick(textData.point, silent = true)
  }

  fun rightClick() {
    LOG.info("Right click at '$this'")
    component.rightClick(textData.point, silent = true)
  }

  fun moveMouse() {
    LOG.info("Move mouse to the $this ${textData.point}")
    component.moveMouse(textData.point, silent = true)
  }
}