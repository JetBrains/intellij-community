package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.RunContentDescriptorRef
import com.intellij.driver.sdk.getRunContentManager
import com.intellij.driver.sdk.go.ui.goTestRunTab
import com.intellij.driver.sdk.go.ui.gutterRunMenuItem
import com.intellij.driver.sdk.go.ui.gutterRunSubmenu
import com.intellij.driver.sdk.go.ui.gutterRunSubmenuItems
import com.intellij.driver.sdk.go.ui.runGutterIcon
import com.intellij.driver.sdk.go.ui.shouldHaveRunOutput
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GoTestActions(val driver: Driver, val ui: UiRobot) {

  fun runTestAtCaret(configurationName: String? = null, timeout: Duration = RUN_TIMEOUT) {
    val previousRun = executionId()
    step("Run the test under the caret") {
      driver.invokeAction("RunClass")
      if (configurationName != null) {
        driver.withContext {
          ui.popup().waitOneContainsText(configurationName).click()
        }
      }
    }
    waitForRunFinished(previousRun, timeout)
  }

  fun runFromGutter(
    line: Int,
    configurationName: String,
    fromSubmenu: Boolean = false,
    timeout: Duration = RUN_TIMEOUT,
  ) {
    val previousRun = executionId()
    step("Run '$configurationName' from the gutter of line $line") {
      driver.invokeAction("ExpandAllRegions")
      driver.withContext {
        ui.ideFrame { runGutterIcon(line).click() }
        if (fromSubmenu) {
          ui.gutterRunSubmenu()
            .shouldBe("The gutter popup has no 'Run' submenu of configurations", present, POPUP_TIMEOUT)
            .click()
          val entries = ui.gutterRunSubmenuItems(configurationName)
          waitFor(
            message = "'$configurationName' is present in the 'Run' submenu",
            timeout = POPUP_TIMEOUT,
            errorMessage = { "'$configurationName' is not present in the 'Run' submenu" },
          ) { entries.list().isNotEmpty() }
          entries.list().first().click()
        }
        else {
          ui.gutterRunMenuItem(configurationName)
            .shouldBe("'$configurationName' is not present in the gutter popup", present, POPUP_TIMEOUT)
            .click()
        }
      }
    }
    waitForRunFinished(previousRun, timeout)
  }

  fun checkExitCode(expected: Int) {
    step("Verify the test process exited with code $expected") {
      driver.withContext {
        waitFor(
          message = "Test process exited with code $expected",
          timeout = ASSERTION_TIMEOUT,
          errorMessage = { "Test process exited with code ${exitCode()} instead of $expected" },
        ) { exitCode() == expected }
      }
    }
  }

  fun checkStatus(vararg texts: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { shouldHaveStatus(*texts) }
      }
    }
  }

  fun checkReportedTests(vararg tests: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { shouldHaveReportedTests(*tests) }
      }
    }
  }

  fun checkResultsTree(vararg nodes: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { shouldHaveNodes(*nodes) }
      }
    }
  }

  fun selectTestNode(node: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { selectNode(node) }
      }
    }
  }

  fun checkTestOutput(vararg texts: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { console.shouldContainTexts(*texts) }
      }
    }
  }

  fun checkRunOutput(vararg texts: String) {
    step("Verify the run output reports ${texts.joinToString(prefix = "[", postfix = "]")}") {
      driver.withContext {
        ui.ideFrame { shouldHaveRunOutput(*texts, timeout = ASSERTION_TIMEOUT) }
      }
    }
  }

  /** Clicks a `file.go:12` link of the output and leaves the caret wherever the link points at. */
  fun navigateFromTestOutput(link: String) {
    driver.withContext {
      ui.ideFrame {
        goTestRunTab { clickOnOutputLink(link) }
      }
    }
  }

  private fun waitForRunFinished(previousRun: Long, timeout: Duration) {
    step("Wait until the test run is finished") {
      driver.withContext {
        var execution = NO_RUN
        waitFor(
          message = "A test run is started",
          timeout = timeout,
          errorMessage = { "The selected run has the execution id $execution, the previous run had $previousRun" },
        ) {
          execution = executionId()
          execution != previousRun
        }
        var terminated: Boolean? = null
        waitFor(
          message = "The test run is finished",
          timeout = timeout,
          errorMessage = {
            if (terminated == null) "The selected run has no process" else "The process of the selected run is still running"
          },
        ) {
          terminated = selectedRun()?.getProcessHandler()?.isProcessTerminated()
          terminated == true
        }
      }
    }
  }

  private fun selectedRun(): RunContentDescriptorRef? =
    driver.getRunContentManager(driver.singleProject()).getSelectedContent()

  /** Identifies a single run, so that a run can be told from the one that occupied the tab before it. */
  private fun executionId(): Long = runCatching { selectedRun()?.getExecutionId() }.getOrNull() ?: NO_RUN

  private fun exitCode(): Int? = runCatching { selectedRun()?.getProcessHandler()?.getExitCode() }.getOrNull()

  private companion object {
    private val RUN_TIMEOUT = 5.minutes

    private val POPUP_TIMEOUT = 30.seconds
    private val ASSERTION_TIMEOUT = 30.seconds

    /** Stands for "no run has been started in this session yet", which no execution id takes. */
    private const val NO_RUN = -1L
  }
}
