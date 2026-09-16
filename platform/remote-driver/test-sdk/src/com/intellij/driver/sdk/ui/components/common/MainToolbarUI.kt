package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.boundsOnScreen
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.openapi.util.SystemInfo
import java.awt.Point
import java.awt.Rectangle

val Finder.mainToolbar: MainToolbarUI
  get() =
    x("//div[@class='MainToolbar']", MainToolbarUI::class.java)


/**
 * On Linux without DISPLAY, we run xvfb without window manager and in this case header is missing and we fallback to maintoolbar
 */
val Finder.toolbar: UiComponent
  get() = if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
    mainToolbar
  } else {
    toolbarHeader
  }


private const val MIN_EMPTY_MAIN_TOOLBAR_AREA_WIDTH = 24

/**
 * Returns the visible main toolbar groups from left to right.
 */
private fun Finder.mainToolbarGroupBounds(): List<Rectangle> =
  xx("//div[@class='MainToolbar']/div").list()
    .map { it.boundsOnScreen }
    .filter { it.width > 0 } // an empty group occupies nothing, but would still split the gap it sits in
    .sortedBy { it.x }

/**
 * Returns the center point of the widest gap between toolbar groups.
 * Returns `null` if no gap is at least [MIN_EMPTY_MAIN_TOOLBAR_AREA_WIDTH] px wide.
 *
 * The point is vertically centered on purpose: the toolbar's top row is the window's top row, where the OS
 * takes the click for its own edge resizing and the IDE never sees it.
 */
fun Finder.emptyMainToolbarAreaPointOnScreenOrNull(): Point? {
  val gaps = mainToolbarGroupBounds().zipWithNext { left, right -> (left.x + left.width) to right.x }

  val (gapStart, gapEnd) = gaps.maxByOrNull { (start, end) -> end - start } ?: return null
  if (gapEnd - gapStart < MIN_EMPTY_MAIN_TOOLBAR_AREA_WIDTH) return null

  val toolbarBounds = mainToolbar.boundsOnScreen
  return Point((gapStart + gapEnd) / 2, toolbarBounds.y + toolbarBounds.height / 2)
}

/**
 * The same as [emptyMainToolbarAreaPointOnScreenOrNull], but fails instead of returning `null`.
 */
fun Finder.emptyMainToolbarAreaPointOnScreen(): Point =
  emptyMainToolbarAreaPointOnScreenOrNull()
  ?: error("No gap of $MIN_EMPTY_MAIN_TOOLBAR_AREA_WIDTH px or more between the groups of the main toolbar " +
           "${mainToolbar.boundsOnScreen}, toolbar groups: ${mainToolbarGroupBounds()}")

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

  fun gitVcsWidget(): AbstractToolbarComboUi =
    abstractToolbarCombo { byType("com.intellij.vcs.git.frontend.widget.GitBranchToolbarComboButton") }
}

fun IdeaFrameUI.openSettingsViaToolbar() {
  step("Open Settings via Toolbar") {
    mainToolbar.settingsButton.click()
    popup().accessibleList().clickItem("Settings", fullMatch = false)
  }
  step("Wait Settings window is opened") {
    settingsDialog().waitFound()
  }
}

val MainToolbarUI.rerunButton get() = x { contains(byAccessibleName("Rerun")) }
val MainToolbarUI.resumeButton get() = x { contains(byAccessibleName("Resume")) }
val MainToolbarUI.pauseButton get() = x { contains(byAccessibleName("Pause")) }
val MainToolbarUI.restartDebugButton get() = x { contains(byAccessibleName("Restart Debug")) }
val MainToolbarUI.stopButton get() = x { contains(byAccessibleName("Stop")) }