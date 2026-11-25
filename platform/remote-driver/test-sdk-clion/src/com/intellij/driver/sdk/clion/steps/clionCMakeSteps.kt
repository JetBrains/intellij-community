package com.intellij.driver.sdk.clion.steps

import com.intellij.driver.sdk.invokeGlobalBackendAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.clionCMakeSteps get() = CLionCMakeSteps(this)

class CLionCMakeSteps(private val ideFrame: IdeaFrameUI) {

  val cmakePanel: UiComponent
    get() = ideFrame.run { x("//div[contains(@class, 'InternalDecoratorImpl')][.//div[contains(@class, 'CMakeConsoleViewImpl')]]") }

  val cmakeConsoleEditor: UiComponent
    get() = ideFrame.run { x("//div[@class='CMakeConsoleViewImpl']//div[@class='EditorComponentImpl']") }

  //** Execute f on the CMake Window (from the left toolbar) */
  fun cmakeView(f: UiComponent.() -> Unit) {
    ideFrame.run {
      leftToolWindowToolbar.cmakeButton.shouldBe { present() }
      leftToolWindowToolbar.cmakeButton.open()
      cmakePanel.f()
      leftToolWindowToolbar.cmakeButton.close()
    }
  }

  /** Reloads CMake project using backend action */
  fun reloadCMakeByInvokeAction() {
    ideFrame.run { // In RemDev mode actions are registered on the backend
      driver.invokeGlobalBackendAction("CMake.ReloadCMakeProject")
      driver.waitForIndicators(1.minutes)
    }
  }

  fun closeCMakeToolWindowIfPresent() {
    ideFrame.leftToolWindowToolbar.cmakeButton.apply {
      if (present()) close()
    }
  }

  fun waitForCMakeReloadMessage(messages: List<String> = listOf("[Finished]"), timeout: Duration = 2.minutes) {
    require(messages.isNotEmpty()) { "At least one CMake reload message is required" }
    cmakeView {
      waitFor(
        message = "CMake reload did not finish or contain any of: ${messages.joinToString()}",
        timeout = timeout,
      ) {
        val output = cmakeConsoleEditor.getAllTexts().asString()
        messages.any { message -> output.contains(message, ignoreCase = true) }
      }
    }
  }

  fun verifyNoErrorsInCMakeOutput() {
    cmakeView {
      val editorTexts = cmakeConsoleEditor.getAllTexts().asString()
      require(!editorTexts.contains("Error")) {
        "CMake output contains errors: $editorTexts"
      }
    }
  }

  private fun getCMakeOutput(): String {
    var result = ""
    cmakeView { result = cmakeConsoleEditor.editor().text }
    return result
  }

  fun checkCMakeOutputWithoutClosingPanel(expected: String) {
    val actual = cmakeConsoleEditor.editor().text
    require(actual.contains(expected)) {
      "CMake output doesn't contain expected text: \n$expected\nActual output: \n$actual\n"
    }
  }

  //* The responsibility of reloading the CMake is to the caller */
  fun checkCMakeOutputContains(expected: String) {
    val actual = getCMakeOutput()
    require(actual.contains(expected)) {
      "CMake output doesn't contain expected text: \n$expected\nActual output: \n$actual\n"
    }
  }

  fun checkCMakeOutputNotContains(notExpected: String) {
    val actual = getCMakeOutput()
    require(!actual.contains(notExpected)) {
      "CMake output does contain: \n$notExpected\nActual output: \n$actual\n"
    }
  }

  fun cmakeSettings(action: SettingsDialogUiComponent.() -> Unit) {
    ideFrame.run {
      openSettingsDialog()
      settingsDialog {
        settingsTree.waitForNodesLoaded()
        settingsTree.should(message = "Settings tree misses CMake", timeout = 5.seconds) {
          pathExists("Build, Execution, Deployment", "CMake")
        }
        openTreeSettingsSection("Build, Execution, Deployment", "CMake")
        action()
        okButton.click()
      }
    }
  }

  fun selectGenerator(generator: String) {
    step("Select CMake generator `$generator`") {
      cmakeSettings { cmakeGeneratorComboBox.selectItem(generator) }
      ideFrame.driver.waitForIndicators(2.minutes)
    }
  }

  private val Finder.cmakeGeneratorComboBox: JComboBoxUiComponent
    get() = comboBox { and(byType("com.jetbrains.cidr.cpp.cmake.settings.CMakeGeneratorComboBox"), byAccessibleName("Generator:")) }

  fun chooseCMakeTab(profileName: String) {
    cmakeView {
      cmakePanel.x {
        and(byClass("ContentTabLabel"), byAccessibleName(profileName))
      }.click()
    }
  }

  fun checkEachCMakeTabContains(expected: String) {
    cmakeView {
      val tabs = cmakePanel.xx(xQuery { byClass("ContentTabLabel") }).list()
      tabs.forEach {
        it.click()
        require(cmakeConsoleEditor.editor().text.contains(expected)) {
          "CMake output doesn't contain expected text: \n$expected\nActual output: \n${cmakeConsoleEditor.editor().text}\n"
        }
      }
    }
  }
}
