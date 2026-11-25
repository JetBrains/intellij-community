package com.intellij.driver.sdk.kubernetes

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import java.awt.Point

fun Finder.checkBoxTree(action: CheckboxTreeUI.() -> Unit) {
  x("//div[@class='CheckboxTree']", CheckboxTreeUI::class.java).action()
}

class CheckboxTreeUI(data: ComponentData): UiComponent(data) {

  fun selectBox(item: String) {
    getAllTexts().drop(3).forEach {
      if (it.text != item)
        click(Point(it.point.x - 15, it.point.y))
    }
  }
}