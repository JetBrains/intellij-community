package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Locators

val IdeaFrameUI.navigationBar get() = x(NavigationBarUi.locator, NavigationBarUi::class.java)

class NavigationBarUi(data: ComponentData): UiComponent(data) {

  companion object {
    val locator = Locators.byClass("NewNavBarPanel")
  }

  val currentPath: List<String>
    get() = findAllText().sortedBy { it.point.x }.map { it.text }

  fun navBarItem(item: String): NavBarItemUi =
    x(Locators.byClassAndAccessibleName("NavBarItemComponent", item), NavBarItemUi::class.java)

  class NavBarItemUi(data: ComponentData): UiComponent(data) {
    val isSelected get() = true
  }
}