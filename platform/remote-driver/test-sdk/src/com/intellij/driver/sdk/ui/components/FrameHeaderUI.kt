package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder

val Finder.toolbarHeader: FrameHeaderUI get() =
  x("//div[@class='MacToolbarFrameHeader' or @class='ToolbarFrameHeader']", FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent get() = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent get() = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent get() = x("//div[@accessiblename='Application icon']")
}