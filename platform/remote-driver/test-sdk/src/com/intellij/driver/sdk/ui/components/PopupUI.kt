package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.should
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow']", PopupUiComponent::class.java)

fun Finder.popupMenu(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyMenu']", PopupMenuUiComponent::class.java)

class PopupMenuUiComponent(data: ComponentData) : UiComponent(data) {
  private val menuItems = xx("//div[@class='ActionMenuItem' or @class='ActionMenu']", PopupItemUiComponent::class.java)

  fun findMenuItemByText(text: String) = menuItems.list().firstOrNull { it.getText() == text}
                                         ?: throw AssertionError("No item with text '$text' found in popup '${this.searchContext}'")

  fun select(vararg items: String) {
    items.forEach { item ->
      should(timeout = 5.seconds) { menuItems.list().map { it.getText() }.contains(item) }
      menuItems.list().first { it.getText() == item }.click()
    }
  }

  fun itemsList() = menuItems.list().map { it.getText() }
}

open class PopupUiComponent(data: ComponentData) : UiComponent(data)

@Remote("com.intellij.openapi.actionSystem.impl.ActionMenuItem")
interface PopupItemRef {
  fun isSelected(): Boolean
  fun getText(): String
}

class PopupItemUiComponent(data: ComponentData) : UiComponent(data) {

  private val popupComponent by lazy { driver.cast(component, PopupItemRef::class) }

  fun getText() = popupComponent.getText()
  fun isSelected() = popupComponent.isSelected()
}