package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import kotlin.time.Duration

private const val TOOL_WINDOW_PANEL_NAME: String = "Python.ProcessOutput.ToolWindowPanel"

/**
 * UI for the "Python Process Output" tool window (id: `PythonProcessOutput`).
 */
fun IdeaFrameUI.pythonProcessOutputToolWindow(action: PythonProcessOutputToolWindowUi.() -> Unit = {}): PythonProcessOutputToolWindowUi =
  x(PythonProcessOutputToolWindowUi::class.java) {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAttribute("name", TOOL_WINDOW_PANEL_NAME)
    )
  }
    .apply(action)

class PythonProcessOutputToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  // --- process tree toolbar (left pane) ---
  val searchField: UiComponent = x { byAttribute("name", SEARCH_FIELD_NAME) }
  val processTreeDisplayOptionsButton: UiComponent = x { byAccessibleName(PROCESS_TREE_DISPLAY_OPTIONS_BUTTON_ACCESSIBLE_NAME) }
  val expandAllButton: UiComponent = x { byAccessibleName(EXPAND_ALL_BUTTON_ACCESSIBLE_NAME) }
  val collapseAllButton: UiComponent = x { byAccessibleName(COLLAPSE_ALL_BUTTON_ACCESSIBLE_NAME) }

  // --- process tree content (left pane) ---
  fun loggedProcessNode(commandSubstring: String, timeout: Duration): UiText =
    x {
      componentWithChild(
        byClass("JBScrollPane"),
        byAttribute("name", PROCESS_TREE_NAME)
      )
    }
      .waitOneContainsText(
        text = commandSubstring,
        message = "Finding at least one logged process with command containing '$commandSubstring'",
        timeout = timeout
      )

  // --- process output (right pane) ---
  val processInfoSection: UiComponent = x { byAttribute("name", INFO_SECTION_NAME) }
  val processOutputSection: UiComponent = x { byAttribute("name", OUTPUT_SECTION_NAME) }
  val copyOutputButton: UiComponent = x { byAccessibleName(COPY_OUTPUT_BUTTON_ACCESSIBLE_NAME) }

  fun text(value: String): UiComponent = x { byVisibleText(value) }

  companion object {
    const val TOOL_WINDOW_ID: String = "PythonProcessOutput"
    const val PROCESS_TREE_NAME: String = "Python.ProcessOutput.Tree"

    const val SEARCH_FIELD_NAME: String = "Python.ProcessOutput.Tree.SearchField"
    const val PROCESS_TREE_DISPLAY_OPTIONS_BUTTON_ACCESSIBLE_NAME: String = "Process Tree Display Options"
    const val EXPAND_ALL_BUTTON_ACCESSIBLE_NAME: String = "Expand All"
    const val COLLAPSE_ALL_BUTTON_ACCESSIBLE_NAME: String = "Collapse All"

    // process output
    const val INFO_SECTION_NAME: String = "Python.ProcessOutput.Output.Info"
    const val OUTPUT_SECTION_NAME: String = "Python.ProcessOutput.Output.Output"
    const val COPY_OUTPUT_BUTTON_ACCESSIBLE_NAME: String = "Copy Output"
  }
}
