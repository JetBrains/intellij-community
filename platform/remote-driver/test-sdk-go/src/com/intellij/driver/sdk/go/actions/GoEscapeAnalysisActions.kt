package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.go.ui.GoEscapeAnalysisUi
import com.intellij.driver.sdk.go.ui.goEscapeAnalysisUi
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.waitEnabled
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A node of the results tree that holds the detail of one compiler message.
 *
 * [path] starts at a node of the top level. [fileName] is the Go file of the message. [line] is its line in that
 * file, counted from 1, as the compiler reported it.
 */
data class GoEscapeAnalysisDetailNode(val path: List<String>, val fileName: String, val line: Int)

/** Drives the Escape Analysis tool of the Go Optimization tool window. */
class GoEscapeAnalysisActions(private val driver: Driver, private val ui: UiRobot) {

  /** Selects the Escape Analysis tool and waits for its panel. Opens the tool window when it is hidden. */
  fun selectEscapeAnalysisTool() {
    step("Select the Escape Analysis tool") {
      driver.withContext {
        openToolWindow().apply {
          toolSelector.should("The Go Optimization tools should appear", TOOL_WINDOW_TIMEOUT) { present() }
          toolSelector.selectItem(ESCAPE_ANALYSIS_TOOL, trimmed = true)
          should("The Escape Analysis panel should appear", TOOL_WINDOW_TIMEOUT) {
            runButton.present() || analyzeForLabel.present() || resultTabs.list().isNotEmpty()
          }
        }
      }
    }
  }

  /** Closes every result tab, so that only the Tools tab stays. */
  fun closeAllResultTabs() {
    step("Close the Escape Analysis result tabs") {
      driver.withContext {
        openToolWindow().apply {
          setFocus()
          for (tab in resultTabs.list()) {
            tab.setFocus()
            tab.rightClick()
            ui.popupMenu().findMenuItemByText("Close Tab").click()
          }
        }
      }
    }
  }

  fun openToolsTab() {
    step("Open the Tools tab of the Go Optimization tool window") {
      driver.withContext {
        openToolWindow().toolsTab.click()
      }
    }
  }

  fun run() {
    step("Run Escape Analysis") {
      driver.withContext {
        openToolWindow().runButton.click()
      }
    }
  }

  /** Waits until the results tree shows [expectedFile]. */
  fun waitForResults(expectedFile: String = MAIN_FILE, timeout: Duration = RESULTS_TIMEOUT) {
    step("Wait for the Escape Analysis results of '$expectedFile'") {
      driver.withContext {
        var results = ""
        openToolWindow().resultsTree.should(
          message = "Escape Analysis results should contain '$expectedFile'",
          timeout = timeout,
          errorMessage = { "Escape Analysis results show: $results" },
        ) {
          results = getAllTexts().asString()
          expectedFile in results
        }
      }
    }
  }

  /** Selects the tool, runs the analysis and waits for the results of [expectedFile]. */
  fun runAndWaitForResults(expectedFile: String = MAIN_FILE, timeout: Duration = RESULTS_TIMEOUT) {
    selectEscapeAnalysisTool()
    run()
    waitForResults(expectedFile, timeout)
  }

  /** The text of the visible nodes of the results tree. */
  fun resultsTreeText(): String = driver.withContext {
    openToolWindow().resultsTree.getAllTexts().asString()
  }

  /**
   * Expands every node of the results tree and returns the number of its levels.
   *
   * The expansion goes through the tree itself, not through the toolbar button, and it waits for the result.
   * Use it to read the full depth of the tree. Use [expandAllResults] to test the button.
   */
  fun expandResultsTree(): Int = step("Expand every node of the results tree") {
    driver.withContext {
      openToolWindow().resultsTree.expandAll().collectExpandedPaths().maxOfOrNull { it.path.size } ?: 0
    }
  }

  /**
   * Expands the results tree and selects its deepest node.
   *
   * Only a leaf holds the detail of one compiler message. Every node above it groups the messages of a directory,
   * a file, a type, a function or a category. The depth of the tree changes with the results, so the deepest node
   * is the reliable way to reach a detail.
   */
  fun selectDetailNode(): GoEscapeAnalysisDetailNode = step("Select a detail node of the results tree") {
    driver.withContext {
      val tree = openToolWindow().resultsTree
      tree.expandAll()
      val deepest = tree.collectExpandedPaths().maxByOrNull { it.path.size }
                    ?: error("The Escape Analysis results tree has no node")
      tree.clickRow(deepest.row)
      GoEscapeAnalysisDetailNode(deepest.path, goFileNameIn(deepest.path), lineOf(deepest.path))
    }
  }

  fun collapseAllResults() {
    step("Collapse the Escape Analysis results tree") {
      driver.withContext {
        clickSideToolbarButton(COLLAPSE_ALL_TOOLTIP)
      }
    }
  }

  fun expandAllResults() {
    step("Expand the Escape Analysis results tree") {
      driver.withContext {
        clickSideToolbarButton(EXPAND_ALL_TOOLTIP)
      }
    }
  }

  /** Waits until the deepest visible path of the results tree has [depth] nodes. */
  fun waitForResultsTreeDepth(depth: Int, timeout: Duration = RESULTS_TIMEOUT) {
    step("Wait for $depth visible levels of the results tree") {
      driver.withContext {
        var paths = emptyList<List<String>>()
        openToolWindow().resultsTree.should(
          message = "The results tree should show $depth levels",
          timeout = timeout,
          errorMessage = { "The results tree shows the paths: $paths" },
        ) {
          paths = collectExpandedPaths().map { it.path }
          paths.maxOfOrNull { it.size } == depth
        }
      }
    }
  }

  /** Waits until the detail pane shows [expectedText]. */
  fun waitForDetailText(expectedText: String, timeout: Duration = RESULTS_TIMEOUT) {
    step("Wait for the detail pane to show '$expectedText'") {
      driver.withContext {
        var detail = ""
        openToolWindow().detailPane.should(
          message = "The detail pane should show '$expectedText'",
          timeout = timeout,
          errorMessage = { "The detail pane shows: $detail" },
        ) {
          detail = getAllTexts().asString()
          expectedText in detail
        }
      }
    }
  }

  /** The name of the Go file in [path]. A file node also shows its counts, so the name needs a match. */
  private fun goFileNameIn(path: List<String>): String =
    path.firstNotNullOfOrNull { GO_FILE_NAME.find(it)?.value }
    ?: error("The results tree path $path has no Go file node")

  /**
   * Clicks the side toolbar button with [tooltip] when the button is enabled.
   *
   * The toolbar of a new result tab can show Collapse All and Expand All disabled after the tree is filled.
   * The toolbar updates them only after an input event, and a click on a disabled button does nothing.
   * The mouse move over the button is that event.
   */
  private fun clickSideToolbarButton(tooltip: String) {
    openToolWindow().sideToolbarButton(tooltip).apply {
      moveMouse()
      waitEnabled(TOOL_WINDOW_TIMEOUT)
      click()
    }
  }

  /** The line of the detail node of [path]. A detail node starts with the line of the compiler message. */
  private fun lineOf(path: List<String>): Int =
    path.last().trim().substringBefore(' ').toIntOrNull()
    ?: error("The results tree node '${path.last()}' does not start with a line number")

  private fun openToolWindow(): GoEscapeAnalysisUi {
    val toolWindow = ui.ideFrame().goEscapeAnalysisUi()
    if (!toolWindow.present()) {
      driver.invokeAction("ActivateGoOptimizationToolWindow", now = false)
      toolWindow.should("The Go Optimization tool window should appear", TOOL_WINDOW_TIMEOUT) { present() }
    }
    return toolWindow
  }

  private companion object {
    const val MAIN_FILE = "main.go"
    const val ESCAPE_ANALYSIS_TOOL = "Escape analysis"
    const val COLLAPSE_ALL_TOOLTIP = "Collapse All"
    const val EXPAND_ALL_TOOLTIP = "Expand All"
    val GO_FILE_NAME = Regex("""\S+\.go""")
    val TOOL_WINDOW_TIMEOUT = 30.seconds
    val RESULTS_TIMEOUT = 30.seconds
  }
}
