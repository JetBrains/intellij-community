package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators

val Finder.toolbarHeader: FrameHeaderUI get() =
  x("//div[@class='MacToolbarFrameHeader' or @class='ToolbarFrameHeader']", FrameHeaderUI::class.java)

class FrameHeaderUI(data: ComponentData) : UiComponent(data) {
  val separateRowMenu: UiComponent get() = x("//div[@class='IdeJMenuBar']")
  val burgerMenuButton: UiComponent get() = x("//div[@tooltiptext='Main Menu']")
  val appIcon: UiComponent get() = x("//div[@accessiblename='Application icon']")
  val projectWidget get() = x(Locators.byVisibleText(driver.singleProject().getName()))
  val runWidget get() = x("//div[@myicon='run.svg']")
  val cwmButton get() = x(Locators.byTooltip("Code With Me"))
  val searchEveryWhereButton get() = x(Locators.byAccessibleName("Search Everywhere"))
  val settingsButton get() = x(Locators.byJavaClassContains("SettingsEntryPoint"))
}