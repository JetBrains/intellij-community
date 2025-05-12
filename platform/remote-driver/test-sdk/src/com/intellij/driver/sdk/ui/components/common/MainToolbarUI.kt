package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
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
  val buildButton: UiComponent get() = x("//div[@myicon='build.svg']")
  val runButton: UiComponent get() = x("//div[@myicon='run.svg']")
  val debugButton: UiComponent get() = x("//div[@myicon='debug.svg']")
  val moreButton: UiComponent get() = x("//div[@myicon='moreVertical.svg']")
  val searchButton: UiComponent get() = x("//div[@myicon='search.svg']")
  val stopButton: UiComponent get() = x("//div[@myicon='stop.svg']")
  val settingsButton: UiComponent get() = x("//div[contains(@myaction, 'Settings')]")
  val runWidget get() = x(ActionButtonUi::class.java) { contains(byJavaClass("com.intellij.execution.ui.RedesignedRunConfigurationSelector")) }
  val cwmButton get() = x { byTooltip("Code With Me") }

  fun projectWidget(projectName: String): AbstractToolbarComboUi =
    abstractToolbarCombo { and(byType("com.intellij.openapi.wm.impl.AbstractToolbarCombo"), contains(byVisibleText(projectName))) }

  fun vcsWidget(branchName: String = "Version"): AbstractToolbarComboUi =
    abstractToolbarCombo { and(byType("com.intellij.openapi.wm.impl.AbstractToolbarCombo"), contains(byVisibleText(branchName))) }
}

val MainToolbarUI.rerunButton get() = x { contains(byAccessibleName("Rerun")) }
val MainToolbarUI.resumeButton get() = x { contains(byAccessibleName("Resume")) }
val MainToolbarUI.pauseButton get() = x { contains(byAccessibleName("Pause")) }
val MainToolbarUI.restartDebugButton get() = x { contains(byAccessibleName("Restart Debug")) }
val MainToolbarUI.stopButton get() = x { contains(byAccessibleName("Stop")) }