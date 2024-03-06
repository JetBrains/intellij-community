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

  private val menuItems
    get() = xx("//div[@class='ActionMenuItem' or @class='ActionMenu']", PopupItemUiComponent::class.java)

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

  fun getText(): String
}

class PopupItemUiComponent(data: ComponentData) : UiComponent(data) {

  private val popupComponent by lazy { driver.cast(component, PopupItemRef::class) }

  fun getText() = popupComponent.getText()
}