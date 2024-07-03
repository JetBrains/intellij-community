package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder

val Finder.mainToolbar: MainToolbarUI get() =
  x("//div[@class='MainToolbar']", MainToolbarUI::class.java)

class MainToolbarUI(data: ComponentData) : UiComponent(data) {
  val vcsWidget: UiComponent get() = x { and(byClass("ToolbarComboButton"), contains(byVisibleText("Version"))) }
  val runButton: UiComponent get() = x("//div[@myicon='run.svg']")
  val debugButton: UiComponent get() = x("//div[@myicon='debug.svg']")
  val moreButton: UiComponent get() = x("//div[@myicon='moreVertical.svg']")
  val searchButton: UiComponent get() = x("//div[@myicon='search.svg']")
  val settingsButton: UiComponent get() = x("//div[contains(@myaction, 'Settings')]")
  val runWidget get() = x(ActionButtonUi::class.java) { contains(byJavaClass("RedesignedRunConfigurationSelector")) }
  val cwmButton get() = x { byTooltip("Code With Me") }

  fun projectWidget(projectName: String): UiComponent =
    x("//div[@class='ToolbarComboButton' and @visible_text='$projectName' and contains(@lefticons_delegate, '20x20])')]")
}