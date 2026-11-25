package com.intellij.driver.sdk.python.steps

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.python.createMainPyCheckbox
import com.intellij.driver.sdk.python.installMinicondaLink
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.common.dialogs.newProjectDialog
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.common.openMenuItem
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

val IdeaFrameUI.projectTemplate get() = ProjectSteps(this)

// The New Project dialog rejects 'Create' while it loads the environments or validates an executable.
private val NEW_PROJECT_DIALOG_BUSY_TEXTS = listOf("Loading", "Wait for the executable validation")

// The dialog can switch the interpreter type after the load and then validate the new executable.
private val NEW_PROJECT_DIALOG_IDLE_PERIOD = 2.seconds

class ProjectSteps(private val ideFrame: IdeaFrameUI) {

  @OptIn(ManualWaitForIndicators::class)
  fun createProject(type: String) {
    ideFrame.driver.withContext {
      step("Create $type project") {
        ui.x("//div[@text='New']").waitFound(1.minutes).click()
        waitForIndicators(3.minutes)
        ui.popup().waitFound().waitOneText("Project…").click()
        newProjectDialog {
          waitFound(3.minutes)
          chooseProjectType(type)

          if (type == "Pure Python") createMainPyCheckbox.check()
          if (type == "Jupyter" && installMinicondaLink.present()) {
            installMinicondaLink.click()
            installMinicondaLink.should("'Install Miniconda' link is still visible after 5 minutes", 5.minutes, notPresent)
          }
          waitUntilIdle()
          takeScreenshot("Create_project_dialog")
          createButton.click()
          waitUntilClosed()
        }
        // The dialog is closed after the click, so look for the popup outside of it.
        if (type == "Jupyter" && ui.popup("//div[contains(@visible_text, 'Install') and contains(@visible_text, 'Failed')]").present()) {
          // Closing popup with failed package installation
          ideFrame.keyboard { enter() }
        }
        waitForIndicators(if (type == "Jupyter") 20.minutes else 10.minutes)
      }
      step("Check that project is opened") {
        val projects = getOpenProjects()

        ideFrame.leftToolWindowToolbar.projectButton.open()
        if (!isRemDevMode) {
          ideFrame.projectView().waitFound(1.minutes)
        }
        assert(projects.size == 1) { "No opened projects. Found ${projects.size} projects" }
      }
    }
  }

  fun checkProjectFiles(files: List<String>) {
    ideFrame.run {
      step("Check files generated on project creation") {
        val notFoundItems = files.filterNot { it in projectView().getAllTexts().asString("||") }
        assert(notFoundItems.isEmpty()) {
          "Expected items (${notFoundItems}) not found in '${projectView().getAllTexts().asString("||")}'"
        }
      }
    }
  }

  fun checkRunConfig(configName: String) {
    ideFrame.run {
      step("Check that '$configName' configuration is created") {
        mainToolbar.runWidget.apply {
          val configButton = x("//div[@class='RedesignedRunConfigurationSelectorButton' and @visible_text='$configName']")
          configButton.shouldBe("'$configName' configuration is not shown") { present() }
        }
      }
    }
  }

  fun runDefaultConfig(result: List<String>) {
    ideFrame.run {
      step("Run default configuration and check result") {
        mainToolbar.runButton.click()
        val runWindow = x("//div[@accessiblename='Editor' and @class='EditorComponentImpl']")
        waitFor(
          message = "The Run tool window shows $result",
          timeout = 30.seconds,
          errorMessage = { texts ->
            if (texts == null) "The Run tool window has no console 30 s after the click on Run"
            else "Run window does not contain ${result.filterNot { it in texts }}: $texts"
          },
          getter = { if (runWindow.present()) runWindow.getAllTexts().joinToString() else null },
          checker = { texts -> texts != null && result.all { it in texts } },
        )
      }
    }
  }

  fun stopDefaultConfig(result: String) {
    ideFrame.run {
      step("Stop default configuration and check result") {
        mainToolbar.stopButton.click()
        val runWindow = x("//div[@accessiblename='Editor' and @class='EditorComponentImpl']")
        runWindow.waitContainsText(result, "Run window does not contain '$result'")
      }
    }
  }

  fun startCreatingProject() {
    ideFrame.run {
      x { byAccessibleName("New") }.click()
      popup().waitFound().waitOneText("Project…").click()
      newProjectDialog { waitFound(3.minutes) }
    }
  }

  private fun NewProjectDialogUI.waitUntilIdle() {
    var lastBusy = TimeSource.Monotonic.markNow()
    waitFor(
      message = "The New Project dialog shows none of $NEW_PROJECT_DIALOG_BUSY_TEXTS for $NEW_PROJECT_DIALOG_IDLE_PERIOD",
      timeout = 1.minutes,
      interval = 500.milliseconds,
      getter = { getAllTexts().map { it.text } },
      checker = { texts ->
        if (texts.any { text -> NEW_PROJECT_DIALOG_BUSY_TEXTS.any { it in text } }) lastBusy = TimeSource.Monotonic.markNow()
        lastBusy.elapsedNow() >= NEW_PROJECT_DIALOG_IDLE_PERIOD
      },
    )
  }

  private fun NewProjectDialogUI.waitUntilClosed() {
    waitFor(
      message = "The New Project dialog closes after the click on 'Create'",
      timeout = 30.seconds,
      errorMessage = { "The New Project dialog is open 30 s after the click on 'Create'. It shows: ${getAllTexts().asString(" | ")}" },
      getter = { present() },
      checker = { open -> !open },
    )
  }
}