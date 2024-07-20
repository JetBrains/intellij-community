package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.openapi.util.SystemInfo

val Finder.mainToolbar: MainToolbarUI get() =
    x("//div[@class='MainToolbar']", MainToolbarUI::class.java)


/**
 * On Linux without DISPLAY, we run xvfb without window manager and in this case header is missing and we fallback to maintoolbar
 */
val Finder.toolbar: UiComponent
  get() = if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
    mainToolbar
  }
  else {
    toolbarHeader
  }


class MainToolbarUI(data: ComponentData) : UiComponent(data) {
  val vcsWidget: UiComponent get() = x { and(byClass("ToolbarComboButton"), contains(byVisibleText("Version"))) }
  val runButton: UiComponent get() = x("//div[@myicon='run.svg']")
  val debugButton: UiComponent get() = x("//div[@myicon='debug.svg']")
  val moreButton: UiComponent get() = x("//div[@myicon='moreVertical.svg']")
  val searchButton: UiComponent get() = x("//div[@myicon='search.svg']")
  val stopButton: UiComponent get() = x("//div[@myicon='stop.svg']")
  val settingsButton: UiComponent get() = x("//div[contains(@myaction, 'Settings')]")
  val runWidget get() = x(ActionButtonUi::class.java) { contains(byJavaClass("RedesignedRunConfigurationSelector")) }
  val cwmButton get() = x { byTooltip("Code With Me") }

  fun projectWidget(projectName: String): UiComponent =
    x("//div[@class='ToolbarComboButton' and @visible_text='$projectName']")
}