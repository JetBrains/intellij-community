package com.intellij.driver.sdk.ui

import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.components.UiComponent

class UiText(private val component: UiComponent, private val textData: TextData) {
  val text = textData.text
  val point = textData.point
  val bundleKey = textData.bundleKey
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