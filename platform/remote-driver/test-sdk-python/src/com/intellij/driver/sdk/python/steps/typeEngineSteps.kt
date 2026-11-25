package com.intellij.driver.sdk.python.steps

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.python.PyTypeEngineSettingsPageUi
import com.intellij.driver.sdk.python.typeEngineSettingsPage
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.waitSelected
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.typeEngine get() = TypeEngineSteps(this)

class TypeEngineSteps(private val ideFrame: IdeaFrameUI) {

  @OptIn(ManualWaitForIndicators::class)
  fun selectTypeEngine(engineName: String) {
    step("Select '$engineName' type engine in settings") {
      ideFrame.waitForIndicators(2.minutes)
      try {
        openTypeEnginePage(engineName)
        ideFrame.settingsDialog {
          typeEngineSettingsPage {
            engineButton(engineName).apply {
              click()
              confirmToolDisableIfAsked()
              waitSelected(true)
            }
          }
          okButton.click()
          waitNotFound(30.seconds)
        }
      }
      finally {
        runCatching { cancelSettingsIfOpen() }
      }
    }
  }

  private fun openTypeEnginePage(engineName: String) {
    for (attempt in 1..OPEN_PAGE_ATTEMPTS) {
      ideFrame.openSettingsDialog()
      val settings = ideFrame.settingsDialog().waitFound(30.seconds)
      settings.searchTextField.text = "Type Engine"
      try {
        settings.typeEngineSettingsPage().engineButton(engineName).waitFound(ENGINE_BUTTON_TIMEOUT)
        return
      }
      catch (e: WaitForException) {
        if (attempt == OPEN_PAGE_ATTEMPTS) throw e
      }
      // PY-93610: the page reads the list of the engines only when it opens.
      cancelSettingsIfOpen()
    }
  }

  private fun PyTypeEngineSettingsPageUi.engineButton(engineName: String): ActionButtonUi =
    when (engineName) {
      "Built-in" -> builtInEngineButton
      "Pyrefly" -> pyreflyEngineButton
      "ty" -> tyEngineButton
      else -> error("Unknown type engine '$engineName'")
    }

  private fun cancelSettingsIfOpen() {
    val settings = ideFrame.settingsDialog()
    if (settings.present()) {
      settings.cancelButton.click()
      settings.waitNotFound(30.seconds)
    }
  }

  /**
   * Switching away from the active tool-backed engine (Pyrefly/ty) opens a modal 'Tool in Use'
   * dialog asking whether to turn the engine's External Tools entry off too. Answer Yes to restore
   * the default state; left unanswered, the dialog blocks the Settings dialog and every later test.
   */
  private fun confirmToolDisableIfAsked() {
    val toolInUseDialog = ideFrame.driver.ui.dialog(title = "Tool in Use")
    try {
      toolInUseDialog.waitFound(5.seconds)
    }
    catch (_: WaitForException) {
      return
    }
    toolInUseDialog.pressButton("Yes")
    toolInUseDialog.waitNotFound(10.seconds)
  }

  @OptIn(ManualWaitForIndicators::class)
  fun verifyTypeCheckerHighlightsTypingProblems(engineName: String, fileName: String, vararg expectedFragments: String) {
    selectTypeEngine(engineName)
    step("Wait for the type engine installation to finish") {
      ideFrame.waitForIndicators(3.minutes)
    }
    step("Open '$fileName' containing a typing problem") {
      ideFrame.driver.openFile(fileName)
    }
    step("Verify the typing problem is highlighted") {
      val document = ideFrame.codeEditorForFile(fileName).document
      waitFor(
        "'$fileName' has a type mismatch highlight mentioning ${expectedFragments.joinToString(", ") { "'$it'" }}",
        timeout = 4.minutes,
        interval = 5.seconds,
      ) {
        ideFrame.driver.getHighlights(document).any { highlight ->
          val description = highlight.getDescription() ?: ""
          expectedFragments.all { description.contains(it) }
        }
      }
    }
  }

  private companion object {
    const val OPEN_PAGE_ATTEMPTS = 3
    val ENGINE_BUTTON_TIMEOUT = 15.seconds
  }
}
