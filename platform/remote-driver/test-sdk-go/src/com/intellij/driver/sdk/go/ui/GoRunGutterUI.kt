package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.GutterUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.gutter
import kotlin.time.Duration.Companion.seconds

internal fun Finder.runGutterIcon(line: Int): GutterUiComponent.GutterIcon {
  // Unlike the rest of the editor API, scrollToPosition takes the line as the editor stores it, 0-based.
  codeEditor().scrollToPosition(line - 1, 1)
  return editorTabs().gutter().icon(
    timeout = ICON_TIMEOUT,
    errorMessage = "No gutter icon on line $line",
  ) { it.line == line - 1 }
}

internal fun Finder.gutterRunMenuItem(nameFragment: String): UiComponent =
  x {
    and(
      byClass("ActionMenuItem"),
      contains(byAccessibleName("Run '$nameFragment")),
      not(contains(byAccessibleName(WITH_COVERAGE))),
    )
  }

internal fun Finder.gutterRunWithCoverageMenuItem(): UiComponent =
  x { and(byClass("ActionMenuItem"), contains(byAccessibleName(WITH_COVERAGE))) }

/** The `Debug '<name>'` item. The Linux-only `Record and Debug '<name>'` item of the rr executor is excluded. */
internal fun Finder.gutterDebugMenuItem(nameFragment: String): UiComponent =
  x {
    and(
      byClass("ActionMenuItem"),
      contains(byAccessibleName("Debug '$nameFragment")),
      not(contains(byAccessibleName(RECORD_AND_DEBUG))),
    )
  }

internal fun Finder.gutterRunSubmenu(): UiComponent =
  x { and(byClass("ActionMenu"), byAccessibleName("Run")) }

internal fun Finder.gutterRunSubmenuItems(nameFragment: String): UIComponentsList<UiComponent> =
  xx { and(byClass("ActionMenuItem"), contains(byAccessibleName(nameFragment))) }

private val ICON_TIMEOUT = 30.seconds

private const val WITH_COVERAGE = "with Coverage"
private const val RECORD_AND_DEBUG = "Record and Debug"
