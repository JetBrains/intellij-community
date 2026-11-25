package com.intellij.driver.sdk.python.steps

import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.requireProject
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.config get() = ConfigSteps(this)

class ConfigSteps(private val ideFrame: IdeaFrameUI) {

  /** Waits for a run configuration to exist, e.g. one the IDE creates itself after detecting a framework. */
  fun waitForConfig(configName: String, timeout: Duration = 1.minutes) {
    step("Wait for $configName run configuration") {
      ideFrame.run {
        val configNames = {
          driver.service<RunManagerRef>(requireProject(), RdTarget.BACKEND).getAllConfigurationsList().map { it.getName() }
        }
        waitFor(message = "Run configuration '$configName' is created",
                timeout = timeout,
                errorMessage = { "Existing run configurations: ${configNames()}" }) {
          configName in configNames()
        }
      }
    }
  }

  fun selectConfig(configName: String) {
    step("Select $configName run configuration") {
      ideFrame.run {
        x { byClass("RedesignedRunConfigurationSelectorButton") }.click()
        popup().waitFound().apply {
          keyboard {
            driver.ui.pasteText(configName)
            enter()
          }
        }
      }
    }
  }

  fun selectInterpreterInEditConfigPopup(interpreterName: String, button: String) {
    step("Select $interpreterName interpreter if Edit Configuration popup appears") {
      ideFrame.run {
        try {
          dialog(title = "Edit Configuration").waitFound(3.seconds).apply {
            waitAnyTextsContains("No interpreter").first().click()
            popup().waitFound().waitAnyTextsContains(interpreterName).first().click()
            popup().waitNotFound()
            button(button).click()
          }
        } catch (_: WaitForException) {
          println("Edit Configuration popup is not shown")
        }
      }
    }
  }
}

@Remote("com.intellij.execution.RunManager", plugin = "com.intellij/intellij.platform.execution")
private interface RunManagerRef {
  fun getAllConfigurationsList(): List<RunConfigurationRef>
}

@Remote("com.intellij.execution.configurations.RunConfiguration", plugin = "com.intellij/intellij.platform.execution")
private interface RunConfigurationRef {
  fun getName(): String
}

