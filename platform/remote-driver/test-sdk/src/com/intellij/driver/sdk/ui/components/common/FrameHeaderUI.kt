package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.boundsOnScreen
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.openapi.util.SystemInfo.isLinux
import java.awt.Point
import java.awt.Rectangle
import kotlin.time.Duration.Companion.seconds

val Finder.toolbarHeader: FrameHeaderUI
  get() = x(xQuery {
    or(byClass("MacToolbarFrameHeader"),  byClass("ToolbarFrameHeader"))
  }, FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent get() = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent get() = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent get() = x("//div[@accessiblename='Application icon']")
}

fun Finder.openMenuItem(vararg items: String) {
  openMenuItem(false, true, *items)
}

fun Finder.openMenuItem(clickOnFirst: Boolean = false, fullMatch: Boolean = true, vararg items: String) {
  if (isLinux) {
    x("//div[@tooltiptext='Main Menu']").click()
  } else {
    toolbarHeader.burgerMenuButton.click()
  }

  if (xx(xQuery { or(byClass("LinuxIdeMenuBar"), byClass("IdeJMenuBar")) }).list().isNotEmpty()) {
    if(clickOnFirst) {
      actionMenuButtonByText(items.first()).apply {
        waitFound(5.seconds)
        click(point = Point(10, 10))
      }
    }
    items.dropLast(1).forEach { path ->
      actionMenuButtonByText(path).apply {
        waitFound(5.seconds)
        moveMouse(point = Point(10, 10))
        moveMouse()
      }
    }
    actionMenuButtonByText(items.last()).click(point = Point(10, 10))
  }
  else {
    val openLists = mutableListOf<Rectangle>()
    items.dropLast(1).forEach { item ->
      val list = menuList(item, openLists)
      list.hoverItem(item, fullMatch, offset = Point(10, 10))
      openLists += list.boundsOnScreen
    }
    menuList(items.last(), openLists).clickItem(items.last(), fullMatch, offset = Point(10, 10))
  }
}

/** Waits for a menu list that shows [item] and is not one of [openLists]. */
private fun Finder.menuList(item: String, openLists: List<Rectangle>): JListUiComponent = waitFor(
  message = "The menu shows a list with '$item'",
  timeout = DEFAULT_FIND_TIMEOUT,
  getter = {
    xx(JListUiComponent::class.java) { contains(byVisibleText(item)) and byJavaClass($$"com.intellij.ui.popup.list.ListPopupImpl$MyList") }
      .list()
      .filter { it.boundsOnScreen !in openLists }
  },
  checker = { it.isNotEmpty() },
).first()

private fun Finder.actionMenuButtonByText(text: String): UiComponent {
  return x(xQuery { or(byClass("LinuxIdeMenuBar"), byClass("IdeJMenuBar")) } + xQuery { and(contains(byClass("ActionMenu")), byText(text)) })
}