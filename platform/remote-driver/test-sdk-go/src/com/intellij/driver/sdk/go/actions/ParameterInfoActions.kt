package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.go.ui.goParameterInfoPopup
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Parameter Info flow: the hint opened by the `ParameterInfo` action (Ctrl+P on Windows/Linux, Cmd+P on macOS)
 * that lists the parameters of the call the caret is in.
 */
class ParameterInfoActions(val driver: Driver, val ui: UiRobot) {

  /** Moves the caret to [line]:[column] and opens the Parameter Info hint. */
  fun showParameterInfo(line: Int, column: Int) {
    step("Invoke Parameter Info at $line:$column") {
      driver.withContext {
        ideFrame {
          codeEditor { goToPosition(line, column) }
        }
      }
      driver.invokeAction("ParameterInfo", now = false)
    }
  }

  /** Wait for the hint and assert that its signature contains every [expectedFragments] entry. */
  fun verifyParameterInfoContains(vararg expectedFragments: String, timeout: Duration = PARAMETER_INFO_TIMEOUT) {
    step("Verify Parameter Info shows: ${expectedFragments.joinToString()}") {
      var signature = ""
      driver.withContext {
        ui.goParameterInfoPopup().should(
          message = "Parameter Info contains ${expectedFragments.joinToString()}",
          timeout = timeout,
          errorMessage = { "Parameter Info shows: $signature" },
        ) {
          signature = this.signature
          expectedFragments.all { signature.contains(it) }
        }
      }
    }
  }

  /**
   * The hint is not focused, so it is dismissed by moving the caret out of the call with a mouse press:
   * a robot Escape does not reliably reach the IDE on CI agents.
   */
  fun closeParameterInfo() {
    step("Close the Parameter Info hint") {
      driver.withContext {
        ideFrame {
          codeEditor { clickOnPosition(FILE_START_LINE, FILE_START_COLUMN) }
        }
        ui.goParameterInfoPopup().shouldBe("Parameter Info hint is still shown", notPresent)
      }
    }
  }

  private companion object {
    private val PARAMETER_INFO_TIMEOUT = 30.seconds

    /** Where [closeParameterInfo] presses the mouse: the first character of the file is outside of any call. */
    private const val FILE_START_LINE = 1
    private const val FILE_START_COLUMN = 1
  }
}
