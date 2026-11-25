package com.intellij.driver.sdk.python.steps

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.python.pythonConsole
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.should
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.console get() = ConsoleSteps(this)

class ConsoleSteps(private val ideFrame: IdeaFrameUI) {

  fun checkConsoleContentContains(pattern: Any, description: String = pattern.toString(), timeout: Duration = 15.seconds) {
    step("Check Python Console content matches '$description'") {
      ideFrame.run {
        pythonConsole {
          when (pattern) {
            is Regex -> {
              should("Python console doesn't contain expected pattern: $description", timeout) {
                pattern.containsMatchIn(content.getAllTexts().asString())
              }
            }
            is String -> {
              should("Python console doesn't contain expected text: '$description' ((${content.getAllTexts().asString()}))", timeout) {
                content.hasSubtext(pattern)
              }
            }
            else -> throw IllegalArgumentException("Pattern must be either Regex or String")
          }
        }
      }
    }
  }

  fun executeInConsole(command: String, promptTimeout: Duration = 30.seconds) {
    step("Execute '$command' in Python console") {
      ideFrame.run {
        should("Python console prompt is not ready in $promptTimeout", promptTimeout) { pythonConsole().promptIsReady() }
        pythonConsole {
          prompt.apply {
            click()
            text = command
            // The caret must stay on the last line. `handleEnterPressed` inserts a newline instead
            // of an execution when the caret is above it.
            moveCaretToOffset(command.length)
          }
          // macOS loses the robot enter key, so the console never ran the command. The toolbar
          // button invokes the same `Console.Execute` action.
          executeButton.performAction()
        }
      }
    }
  }

  fun invokeCompletionPopup(input: String, suggestion: String) {
    step("Invoke completion popup for $suggestion") {
      ideFrame.run {
        pythonConsole {
          prompt.apply {
            click()
            // macOS loses the robot key, so no character reaches the prompt. The set text needs
            // no key event, and it opens no auto-popup. The action below opens the popup.
            text = input
            moveCaretToOffset(input.length)
          }
        }
        driver.invokeAction("CodeCompletion")
        popup().waitContainsText(suggestion, "Completion popup with $suggestion is not shown")
      }
    }
  }

  fun checkPackageVersionInConsole(packageName: String, expectedVersion: String) {
    step("Check that $packageName has version $expectedVersion") {
      ideFrame.run {
        driver.invokeAction("ActivatePythonConsoleToolWindow")
        console.executeInConsole("import $packageName" +
                                 "\nprint($packageName.__version__)")
        should("Python console does not contain expected version $expectedVersion. Actual: ${pythonConsole().content}") {
          pythonConsole().content.hasSubtext(expectedVersion)
        }
      }
    }
  }
}