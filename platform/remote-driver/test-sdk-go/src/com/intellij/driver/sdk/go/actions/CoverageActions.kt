package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.go.ui.coverageLines
import com.intellij.driver.sdk.go.ui.gutterRunWithCoverageMenuItem
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.CoverageToolWindowUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.coverageToolWindow
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CoverageActions(val driver: Driver, val ui: UiRobot) {

  fun runWithCoverageAtCaret() {
    step("Run the configuration under the caret with coverage") {
      driver.invokeAction("RunCoverage")
    }
  }

  fun runWithCoverageFromGutter(testFunctionLine: Int) {
    step("Run with coverage from the editor gutter") {
      driver.withContext {
        ui.ideFrame {
          val runIcon = gutter().getGutterIcons()
                          .filter { runCatching { it.getIconPath() }.getOrNull()?.contains("run", ignoreCase = true) == true }
                          .minByOrNull { kotlin.math.abs(it.line - (testFunctionLine - 1)) }
                        ?: error("No run gutter icon found near line $testFunctionLine")
          runIcon.click()
        }
        ui.gutterRunWithCoverageMenuItem()
          .shouldBe("'… with Coverage' action is not present in the gutter popup", present, 30.seconds)
          .click()
      }
    }
  }

  fun openCoverageToolWindow(timeout: Duration = 3.minutes) {
    step("Open the 'Coverage' tool window") {
      driver.withContext {
        ui.ideFrame {
          rightToolWindowToolbar.stripeButton("Coverage")
            .shouldBe("The 'Coverage' stripe button is not present after $timeout", present, timeout)
            .open()
          coverageToolWindow {
            shouldBe("Coverage report ('CoverageView') is not present after $timeout", present, timeout)
          }
        }
      }
    }
  }

  fun checkCoverageReport(texts: List<String>, timeout: Duration = 2.minutes) {
    require(texts.isNotEmpty()) { "expected texts must not be empty" }
    step("Check coverage report contains ${texts.joinToString(prefix = "[", postfix = "]")}") {
      driver.withContext {
        ui.ideFrame {
          coverageToolWindow {
            var lastContent = ""
            var lastMissing: List<String> = texts
            reportTable.should(
              message = "Coverage report is missing $texts",
              timeout = timeout,
              errorMessage = { "coverage report is missing $lastMissing; actual report text: $lastContent" },
            ) {
              lastContent = reportText()
              lastMissing = texts.filterNot { lastContent.contains(it) }
              lastMissing.isEmpty()
            }
          }
        }
      }
    }
  }

  fun checkInlineCoverageActive(timeout: Duration = 2.minutes) {
    step("Check inline coverage annotations are active (current coverage suite is loaded)") {
      driver.withContext {
        waitFor(
          message = "The current coverage suites bundle is present",
          timeout = timeout,
          errorMessage = { "CoverageDataManager has no current coverage suites bundle" },
        ) {
          service(CoverageDataManagerRef::class, singleProject(), RdTarget.BACKEND).getCurrentSuitesBundle() != null
        }
      }
    }
  }

  fun checkLineCoverage(file: String, coveredLine: String, uncoveredLine: String, timeout: Duration = 2.minutes) {
    step("Check covered and uncovered lines in $file") {
      driver.openFile(file)
      driver.withContext {
        ui.ideFrame {
          codeEditor {
            val lines = text.lines()
            val covered = lines.indexOfFirst { it.trim() == coveredLine } + 1
            val uncovered = lines.indexOfFirst { it.trim() == uncoveredLine } + 1
            require(covered > 0 && uncovered > 0) { "The coverage lines are missing from $file" }
            val expected = mapOf(covered to "LINE_FULL_COVERAGE", uncovered to "LINE_NONE_COVERAGE")
            var actual = emptyMap<Int, String>()
            should("The editor marks the expected coverage lines", timeout, errorMessage = { "Expected $expected, got $actual" }) {
              actual = coverageLines()
              expected.all { (line, key) -> actual[line] == key }
            }
          }
        }
      }
    }
  }

  fun checkPartialStatementCoverage(timeout: Duration = 2.minutes) {
    step("Check the statement coverage percentage") {
      driver.withContext {
        ui.ideFrame {
          coverageToolWindow {
            var actual = ""
            reportTable.should("The report shows partial statement coverage", timeout, errorMessage = { actual }) {
              actual = reportText()
              Regex("(\\d+(?:[.,]\\d+)?)% statements").findAll(actual).any { match ->
                val percentage = match.groupValues[1].replace(',', '.').toDouble()
                percentage > 0 && percentage < 100
              }
            }
          }
        }
      }
    }
  }

  /**
   * The text of the coverage report.
   * The statistics cells come from the accessible names of the cell renderers.
   * A narrow statistics column clips the rendered text of a cell, but not its accessible name.
   */
  private fun CoverageToolWindowUI.reportText(): String {
    val statistics = reportTable.accessibleTable().content().values.flatMap { it.values }
    return (listOf(reportTable.getAllTexts().asString()) + statistics).joinToString(" ")
  }

  fun hideCoverage(timeout: Duration = 2.minutes) {
    step("Hide coverage and check the editor markers") {
      driver.withContext {
        val manager = service(CoverageDataManagerRef::class, singleProject(), RdTarget.BACKEND)
        if (manager.getCurrentSuitesBundle() != null) {
          invokeAction("HideCoverage", now = true)
        }
        waitFor("The coverage suite is cleared", timeout) { manager.getCurrentSuitesBundle() == null }
        ui.ideFrame {
          codeEditor().should("The coverage markers are removed", timeout) { coverageLines().isEmpty() }
        }
      }
    }
  }

  @Remote("com.intellij.coverage.CoverageDataManagerImpl", plugin = "com.intellij.platform.coverage/intellij.platform.coverage")
  interface CoverageDataManagerRef {
    fun getCurrentSuitesBundle(): CoverageSuitesBundleRef?
  }

  @Remote("com.intellij.coverage.CoverageSuitesBundle", plugin = "com.intellij.platform.coverage/intellij.platform.coverage")
  interface CoverageSuitesBundleRef {
    fun getPresentableName(): String
  }
}
