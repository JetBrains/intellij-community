package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfo.isLinux
import java.awt.Point

val Finder.toolbarHeader: FrameHeaderUI
  get() = x(xQuery {
    or(byClass("MacToolbarFrameHeader"), if (isLinux) byClass("MainToolbar") else byClass("ToolbarFrameHeader"))
  }, FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent get() = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent get() = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent get() = x("//div[@accessiblename='Application icon']")
}

fun Finder.openMenuItem(vararg items: String) {
  if (isLinux) {
    x("//div[@tooltiptext='Main Menu']").click()
  } else {
    toolbarHeader.burgerMenuButton.click()
  }


  if (xx(xQuery { or(byClass("LinuxIdeMenuBar"), byClass("IdeJMenuBar")) }).list().isNotEmpty()) {
    items.dropLast(1).forEach { path ->
      actionMenuButtonByText(path).moveMouse(point = Point(10, 10))
    }
    actionMenuButtonByText(items.last()).click(point = Point(10, 10))
  }
  else {
    items.dropLast(1).forEach { path ->
      jBlist(xQuery { and(contains(byVisibleText(path)), byClass("MyList")) }).hoverItem(path, offset = Point(10, 10))
    }
    jBlist(xQuery { contains(byVisibleText(items.last())) }).clickItem(items.last(), offset = Point(10, 10))
  }
}

private fun Finder.actionMenuButtonByText(text: String): UiComponent {
  return x(xQuery { or(byClass("LinuxIdeMenuBar"), byClass("IdeJMenuBar")) } + xQuery { and(contains(byClass("ActionMenu")), byText(text)) })
}