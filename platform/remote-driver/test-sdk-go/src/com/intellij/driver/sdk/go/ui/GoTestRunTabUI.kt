package com.intellij.driver.sdk.go.ui

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.go.GoTestToolWindowUI
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import kotlin.time.Duration.Companion.seconds

internal fun Finder.goTestRunTab(action: GoTestRunTabUI.() -> Unit = {}): GoTestRunTabUI =
  x(GoTestRunTabUI::class.java) { byClass("SMTestRunnerResultsForm") }.apply(action)

class GoTestRunTabUI(data: ComponentData) : UiComponent(data) {

  val resultsTree: JTreeUiComponent = tree(xQuery { byClass("SMTRunnerTestTreeView") })

  val console: GoTestToolWindowUI = x(GoTestToolWindowUI::class.java) { byClass("TestsConsoleViewImpl") }

  private val statusLine: UiComponent = x { byClass("TestStatusLine") }

  val statusText: String
    get() = normalized(statusLine.getAllTexts().asString(" ")).trim()

  fun shouldHaveStatus(vararg texts: String) {
    require(texts.isNotEmpty()) { "texts must not be empty" }
    step("Verify the status line reports ${texts.joinToString(prefix = "[", postfix = "]")}") {
      var status = ""
      var missing = texts.toList()
      should(
        message = "Status line reports ${texts.joinToString(prefix = "[", postfix = "]")}",
        errorMessage = { "Status line is missing $missing. Actual status line: '$status'" },
      ) {
        status = statusText
        missing = texts.filterNot { it in status }
        missing.isEmpty()
      }
    }
  }

  /**
   * The names of the tests that the run reported, the passed ones included. The tree hides the passed tests, so the
   * names come from the test model, not from the tree.
   */
  val reportedTests: List<String>
    get() = driver.cast(component, SMTestRunnerResultsFormRef::class).getTestsRootNode().getAllTests()
      .filterNot { it.isSuite() }
      .map { it.getName() }

  /**
   * Checks that the run reported every test in [tests], whatever the outcome of each. A test is named as the
   * framework reports it, e.g. `WorktreeSuite.TestCommitInitial` for a gocheck method.
   */
  fun shouldHaveReportedTests(vararg tests: String) {
    require(tests.isNotEmpty()) { "tests must not be empty" }
    step("Verify the run reported ${tests.size} tests ${tests.joinToString(prefix = "[", postfix = "]")}") {
      var actual = emptyList<String>()
      var missing = tests.toList()
      should(
        message = "The run reported ${tests.joinToString(prefix = "[", postfix = "]")}",
        errorMessage = { "The run did not report $missing. Reported tests: $actual" },
      ) {
        actual = reportedTests
        missing = tests.filterNot { it in actual }
        missing.isEmpty()
      }
    }
  }

  fun collectNodes(): List<String> = resultsTree.expandAll(EXPAND_TIMEOUT).collectExpandedPathsAsStrings()

  /**
   * Checks that the tree has a node per expected path, matched by the tail of the path — `TestFailing` finds
   * the `Test Results/TestFailing` node.
   */
  fun shouldHaveNodes(vararg nodes: String) {
    require(nodes.isNotEmpty()) { "nodes must not be empty" }
    step("Verify the results tree contains ${nodes.joinToString(prefix = "[", postfix = "]")}") {
      var actual = emptyList<String>()
      var matchedAgainst = emptyList<String>()
      var missing = nodes.toList()
      should(
        message = "Results tree contains ${nodes.joinToString(prefix = "[", postfix = "]")}",
        errorMessage = { "Results tree is missing $missing. Actual nodes: $actual, matched against: $matchedAgainst" },
      ) {
        actual = collectNodes()
        matchedAgainst = actual.map { withoutDurations(it) }
        missing = nodes.filter { node -> matchedAgainst.none { it.endsWith(node) } }
        missing.isEmpty()
      }
    }
  }

  /**
   * Clicks a link of the output, e.g. the `panicking_test.go:7` part of the absolute path a Go stack trace
   * prints, and leaves the caret wherever the link points at.
   */
  fun clickOnOutputLink(text: String) {
    step("Click '$text' in the test output") {
      val output = console.getEditorText()
      val index = output.indexOf(text)
      require(index >= 0) { "'$text' is not in the test output:\n$output" }

      val middleOfLink = index + text.length / 2
      val upToLink = output.take(middleOfLink)
      val line = upToLink.count { it == '\n' } + 1
      val column = middleOfLink - upToLink.lastIndexOf('\n')
      console.editor.clickOnPosition(line, column)
    }
  }

  /** Selects a node so that the console below the tree shows the output of that node alone. */
  fun selectNode(node: String) {
    step("Select '$node' in the results tree") {
      resultsTree.expandAll(EXPAND_TIMEOUT).clickRow { withoutDurations(it).endsWith(node) }
    }
  }

  private fun withoutDurations(path: String): String =
    path.split(PATH_SEPARATOR).joinToString(PATH_SEPARATOR) { normalized(it).replace(DURATION, "") }

  private fun normalized(text: String): String = text.replace(WHITESPACE, " ")

  private companion object {
    /**
     * Whitespace, Unicode separators included. The renderer puts a thin space (U+2009) between a duration and
     * its unit, and neither `\s` nor a plain space matches that.
     */
    private val WHITESPACE = Regex("""[\s\p{Zs}]+""")

    private const val PATH_SEPARATOR = "/"

    /**
     * How long a node took, appended to its name by the renderer: `TestFailing 0 ms`, `Test Results 1 sec 20 ms`.
     * It is stripped before matching, so that a test is named by its name alone. Matched on normalized text.
     * Longer units come first, so that `sec` is not cut down to the `s` of a leftover `ec`.
     */
    private val DURATION = Regex("""(\s\d+\s?(?:ms|sec|min|s|m|h))+$""")

    private val EXPAND_TIMEOUT = 30.seconds
  }
}

@Remote("com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm",
        plugin = "com.intellij.platform.testRunner/intellij.platform.smRunner")
private interface SMTestRunnerResultsFormRef {
  fun getTestsRootNode(): SMTestProxyRef
}

@Remote("com.intellij.execution.testframework.sm.runner.SMTestProxy", plugin = "com.intellij.platform.testRunner/intellij.platform.smRunner")
private interface SMTestProxyRef {
  fun getName(): String

  fun isSuite(): Boolean

  /** This node and every node below it. */
  fun getAllTests(): List<SMTestProxyRef>
}
