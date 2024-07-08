package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.openapi.util.SystemInfo.isLinux

val Finder.toolbarHeader: FrameHeaderUI
  get() = x(xQuery {
    or(byClass("MacToolbarFrameHeader"), if (isLinux) byClass("MainToolbar") else byClass("ToolbarFrameHeader"))
  }, FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent get() = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent get() = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent get() = x("//div[@accessiblename='Application icon']")
}