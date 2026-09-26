package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.Icon
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitForOne
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow']", PopupUiComponent::class.java)

fun Finder.popupLux(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='LuxFrontendWindow']", PopupUiComponent::class.java)

fun Finder.popups(): UIComponentsList<UiComponent> = xx("//div[@class='HeavyWeightWindow']")

fun Finder.popupMenu(locator: QueryBuilder.() -> String = { byClass("MyMenu") }) =
  x(xQuery(locator), PopupMenuUiComponent::class.java)

class PopupMenuUiComponent(data: ComponentData) : UiComponent(data) {
  private val menuItems =
    xx("//div[@class='ActionMenuItem' or @class='ActionMenu']", PopupItemUiComponent::class.java)

  fun getMenuItems(): List<PopupItemUiComponent> = menuItems.list()
  fun getSelectedPath(): List<String> =
    driver.utility(MenuSelectionManager::class).defaultManager().getSelectedPath().mapNotNull {
      it.getComponent().getAccessibleContext()?.getAccessibleName()
    }

  fun findMenuItemByText(text: String) = menuItems.list().firstOrNull { it.getText() == text }
                                         ?: throw AssertionError("No item with text '$text' found in popup '${this.searchContext}'")

  fun select(vararg items: String) {
    items.forEach { item ->
      waitForOne(message = "Find item: $item", timeout = 5.seconds,
              getter = { menuItems.list() },
              checker = { it.getText() == item })
        .click()
    }
  }

  fun selectContains(vararg items: String) {
    items.forEach { item ->
      waitForOne(message = "Find item: $item", timeout = 5.seconds,
                 getter = { menuItems.list() },
                 checker = { it.getText().contains(item) })
        .click()
    }
  }

  fun itemsList() = getMenuItems().map { it.getText() }

  @Remote("javax.swing.MenuSelectionManager")
  private interface MenuSelectionManager {
    fun defaultManager(): MenuSelectionManager
    fun getSelectedPath(): Array<MenuItem>
  }

  @Remote("javax.swing.MenuElement")
  private interface MenuItem {
    fun getComponent(): Component
  }
}

open class PopupUiComponent(data: ComponentData) : WindowUiComponent(data) {
  fun close() {
    val popup = driver.utility<PopupUtil>().getPopupFor(driver.cast(component, Window::class))
    if (popup != null) {
      driver.withContext(OnDispatcher.EDT) { popup.cancel() }
    }
    else {
      dispose()
    }
  }

  @Remote("com.intellij.openapi.ui.popup.util.PopupUtil")
  private interface PopupUtil {
    fun getPopupFor(window: Window): JBPopup?
  }

  @Remote("com.intellij.openapi.ui.popup.JBPopup")
  private interface JBPopup {
    fun cancel()
  }
}

@Remote("com.intellij.openapi.actionSystem.impl.ActionMenuItem")
interface PopupItemRef {
  fun isSelected(): Boolean
  fun getText(): String
  fun getIcon(): Icon
}

class PopupItemUiComponent(data: ComponentData) : UiComponent(data) {
  private val popupComponent by lazy { driver.cast(component, PopupItemRef::class) }

  fun getText() = popupComponent.getText()
  fun isSelected() = popupComponent.isSelected()
  fun getIconPath() = "path=(.*),".toRegex().find(popupComponent.getIcon().toString())?.let { it.groups.last()?.value ?: "empty" }
                      ?: "empty"

  override fun toString(): String = super.toString() + " '" + getText() + "'"
}