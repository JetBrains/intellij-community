package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow']", PopupUiComponent::class.java)

fun Finder.popupMenus(@Language("xpath") xpath: String? = null) =
  xx(xpath ?: "//div[@class='JBPopupMenu']", PopupUiComponent::class.java).list()

open class PopupUiComponent(data: ComponentData) : UiComponent(data) {

  fun select(item: String, vararg subItem: String) {
    findText(item).click()
    subItem.forEach { i ->
      popupMenus().map { it.findAllText() }.flatten().first { it.text == i }.click()
    }
  }
}