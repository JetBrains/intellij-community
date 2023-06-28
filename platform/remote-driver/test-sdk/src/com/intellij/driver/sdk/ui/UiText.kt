package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.remote.TextData

class UiText(private val component: UiComponent, private val textData: TextData) {
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