package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.GutterUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import javax.swing.JList
import kotlin.time.Duration.Companion.seconds

/**
 * The navigation gutter icon of [tooltipTitle] on [line].
 *
 * The gutter keeps an icon by visual line, so the caller expands the foldings of the editor first.
 */
internal fun Finder.navigationGutterIcon(line: Int, tooltipTitle: String): GutterUiComponent.GutterIcon {
  // Unlike the rest of the editor API, scrollToPosition takes the line as the editor stores it, 0-based.
  codeEditor().scrollToPosition(line - 1, 1)
  return editorTabs().gutter().icon(
    timeout = ICON_TIMEOUT,
    errorMessage = "No '$tooltipTitle' gutter icon on line $line",
  ) { it.line == line - 1 && it.mark.getTooltipText()?.contains(tooltipTitle) == true }
}

/** The pop-up of the targets a Go navigation action has found. */
internal fun Finder.navigationTargetsPopup(): GoNavigationTargetsPopupUi =
  x(GoNavigationTargetsPopupUi::class.java) {
    componentWithChild(byClass("HeavyWeightWindow"), byType(JList::class.java))
  }

/**
 * The pop-up gives its targets as the text that the target list paints, one row per line.
 * A target of the project comes before a target of the module cache.
 */
internal class GoNavigationTargetsPopupUi(data: ComponentData) : PopupUiComponent(data) {
  private val targetList: UiComponent = x { byType(JList::class.java) }

  /** The text of each target, from top to bottom. */
  fun targets(): List<String> = targetRows().map { row -> row.joinToString(" ") { it.text } }

  /** Clicks the target at [index]. */
  fun clickTarget(index: Int) {
    targetRows()[index].first().click()
  }

  private fun targetRows(): List<List<UiText>> = targetList.getAllVerticallyOrderedUiText()
}

private val ICON_TIMEOUT = 30.seconds
