package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.jdk.isRemoteMac
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.PopupMenuUiComponent
import com.intellij.driver.sdk.ui.components.elements.listPopup
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JMenuBar
import javax.swing.JPopupMenu
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.toolbarHeader: FrameHeaderUI
  get() = x(xQuery {
    or(byClass("MacToolbarFrameHeader"),  byClass("ToolbarFrameHeader"))
  }, FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent = x("//div[@accessiblename='Application icon']")
}

fun IdeaFrameUI.openMenuItem(vararg items: String) {
  openMenuItem(false, true, *items)
}

fun IdeaFrameUI.openMenuItem(clickOnFirst: Boolean = false, fullMatch: Boolean = true, vararg items: String) {
  x("//div[@tooltiptext='Main Menu']").click()

  if (driver.isRemoteMac) {
    listPopup().select(*items, fullMatch = fullMatch)
  }
  else {
    val menuBar = x { byType(JMenuBar::class.java) }
    menuBar.actionMenuButtonByText(items.first(), fullMatch).apply {
      waitFound(5.seconds)
      if (clickOnFirst) {
        click()
      }
      moveMouse()
    }
    menuBar.x(PopupMenuUiComponent::class.java) { byType(JPopupMenu::class.java) }.let {
      if (fullMatch) {
        it.select(*items.drop(1).toTypedArray())
      }
      else {
        it.selectContains(*items.drop(1).toTypedArray())
      }
    }
  }
}

private fun Finder.actionMenuButtonByText(text: String, fullMatch: Boolean): UiComponent {
  return x {
    and(byType("com.intellij.openapi.actionSystem.impl.ActionMenu"),
        if (fullMatch) byAccessibleName(text) else contains(byAccessibleName(text)))
  }
}