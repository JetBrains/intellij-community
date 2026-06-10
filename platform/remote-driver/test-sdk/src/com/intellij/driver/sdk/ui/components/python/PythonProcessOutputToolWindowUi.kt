package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UIComponentsList.Companion.waitAny
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import kotlin.time.Duration

/**
 * UI for the "Python Process Output" tool window (id: `PythonProcessOutput`).
 *
 * It is a Compose/Jewel tool window, so its elements are matched by the Compose attributes exposed by the
 * remote-driver Compose extension: `testtag` (from `Modifier.testTag`), `visible_text` (from Compose `Text`)
 * and `contentdescription`.
 */
fun IdeaFrameUI.pythonProcessOutputToolWindow(action: PythonProcessOutputToolWindowUi.() -> Unit = {}): PythonProcessOutputToolWindowUi =
  x(PythonProcessOutputToolWindowUi::class.java) {
    componentWithChild(byClass("InternalDecoratorImpl"), byAttribute("contentdescription", "Search"))
  }.apply(action)

class PythonProcessOutputToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  // --- process tree toolbar (left pane) ---
  val searchField: UiComponent = x { byAttribute("contentdescription", "Search") }
  val viewOptionsButton: UiComponent = x { byAttribute("testtag", FILTERS_BUTTON) }
  val expandAllButton: UiComponent = x { byAttribute("testtag", EXPAND_ALL_BUTTON) }
  val collapseAllButton: UiComponent = x { byAttribute("testtag", COLLAPSE_ALL_BUTTON) }

  // --- process tree content (left pane) ---
  /**
   * Waits for at least one logged process whose command contains [commandSubstring] and returns the first one.
   * The row icon test tags are not reliably exposed across OSes, but the command `visible_text` is, and several
   * identical rows may exist (e.g. two `poetry check --lock` runs), so we match by command text and take the first.
   */
  fun loggedProcess(commandSubstring: String, timeout: Duration): UiComponent =
    waitAny(message = "Finding at least one logged process with command containing '$commandSubstring'", timeout = timeout) {
      contains(byVisibleText(commandSubstring))
    }.first()

  // --- process output (right pane) ---
  val processInfoSection: UiComponent = x { byAttribute("testtag", INFO_SECTION) }
  val processOutputSection: UiComponent = x { byAttribute("testtag", OUTPUT_SECTION) }
  val outputContent: UiComponent = x { byAttribute("testtag", OUTPUT_SECTION_CONTENT) }
  val copyOutputButton: UiComponent = x { byAttribute("testtag", COPY_OUTPUT_BUTTON) }

  fun text(value: String): UiComponent = x { byVisibleText(value) }

  companion object {
    const val TOOL_WINDOW_ID: String = "PythonProcessOutput"

    // process tree
    const val FILTERS_BUTTON: String = "ProcessOutput.Tree.FiltersButton"
    const val EXPAND_ALL_BUTTON: String = "ProcessOutput.Tree.ExpandAllButton"
    const val COLLAPSE_ALL_BUTTON: String = "ProcessOutput.Tree.CollapseAllButton"

    // process output
    const val INFO_SECTION: String = "ProcessOutput.Output.InfoSection"
    const val OUTPUT_SECTION: String = "ProcessOutput.Output.OutputSection"
    const val OUTPUT_SECTION_CONTENT: String = "ProcessOutput.Output.OutputSectionContent"
    const val COPY_OUTPUT_BUTTON: String = "ProcessOutput.Output.CopyButton"
  }
}
