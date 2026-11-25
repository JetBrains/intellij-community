package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.terminalToolWindow
import com.intellij.driver.sdk.ui.components.common.toolwindows.TerminalToolWindowUi
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The terminal tool window: starting a shell session, running a command in it and reading its output.
 */
class TerminalActions(val driver: Driver, val ui: UiRobot) {

  private val terminalToolWindow: TerminalToolWindowUi by lazy { ui.ideFrame().terminalToolWindow() }
  private val terminalEditor: JEditorUiComponent by lazy { terminalToolWindow.terminalEditor }

  /** The whole text of the active terminal session: the prompts, the typed commands and their output. */
  val text: String get() = terminalEditor.text

  /**
   * Open the terminal tool window and wait until the started shell has printed its prompt.
   */
  fun openTerminal(timeout: Duration = SHELL_STARTUP_TIMEOUT) {
    step("Open the terminal tool window") {
      ui.ideFrame().leftToolWindowToolbar.terminalButton.open()
      terminalEditor.waitFound(timeout)
      waitForIdleShell(timeout)
    }
  }

  /**
   * Type [command] into the terminal and run it.
   *
   * Waits for the prompt first, because characters typed into a shell that is still starting up are lost.
   */
  fun executeCommand(command: String, timeout: Duration = SHELL_STARTUP_TIMEOUT) {
    step("Run '$command' in the terminal") {
      waitForIdleShell(timeout)
      terminalEditor.run {
        click()
        keyboard {
          typeText(command)
          enter()
        }
      }
    }
  }

  /**
   * Wait until the terminal session text matches every [expected] pattern.
   *
   * The patterns are matched against a single line each, since a [Regex] dot does not match a line break.
   */
  fun waitForOutput(vararg expected: Regex, timeout: Duration = OUTPUT_TIMEOUT) {
    step("Verify the terminal output matches: ${expected.joinToString()}") {
      waitFor(message = "Terminal output matches ${expected.joinToString()}",
              timeout = timeout,
              errorMessage = { "Terminal output was:\n$text" }) {
        val output = text
        expected.all { it.containsMatchIn(output) }
      }
    }
  }

  /**
   * Wait until the terminal has printed something and its text has stopped changing for [SHELL_IDLE_INTERVAL].
   *
   * A shell which has printed its prompt and waits for input is idle, whatever the prompt looks like.
   */
  private fun waitForIdleShell(timeout: Duration) {
    var previous = ""
    waitFor(message = "The shell is idle",
            timeout = timeout,
            interval = SHELL_IDLE_INTERVAL,
            errorMessage = { "Terminal text was:\n$text" }) {
      val current = text
      val idle = current.isNotBlank() && current == previous
      previous = current
      idle
    }
  }

  private companion object {
    val SHELL_IDLE_INTERVAL = 2.seconds
    val SHELL_STARTUP_TIMEOUT = 2.minutes
    val OUTPUT_TIMEOUT = 2.minutes
  }
}
