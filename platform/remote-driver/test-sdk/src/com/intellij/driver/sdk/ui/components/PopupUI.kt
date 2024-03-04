package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow' or @class='JBPopupMenu']", PopupUiComponent::class.java)

fun Finder.popups(@Language("xpath") xpath: String? = null) =
  xx(xpath ?: "//div[@class='HeavyWeightWindow' or @class='JBPopupMenu']", PopupUiComponent::class.java).list()

open class PopupUiComponent(data: ComponentData) : UiComponent(data) {

  fun select(item: String, vararg subItem: String) {
    findText(item).click()
    subItem.forEach { i ->
      popups().map { it.findAllText() }.flatten().first { it.text == i }.click()
    }
  }

  val items
    get() = xx("//div[@class='ActionMenuItem']", PopupItemUiComponent::class.java).list()

  fun itemsList() = items.map { it.getText() }
}

@Remote("com.intellij.openapi.actionSystem.impl.ActionMenuItem")
interface PopupItemRef {

  fun getText(): String
}

class PopupItemUiComponent(data: ComponentData) : UiComponent(data) {

  private val popupComponent by lazy { driver.cast(component, PopupItemRef::class) }

  fun getText() = popupComponent.getText()
}