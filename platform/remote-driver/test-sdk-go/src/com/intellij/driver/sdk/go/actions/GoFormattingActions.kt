package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.go.ui.gofmtOnReformatCheckBox
import com.intellij.driver.sdk.go.ui.openGoCodeStyleOtherTab
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.shouldBe
import kotlin.time.Duration.Companion.seconds

class GoFormattingActions(val driver: Driver, val ui: UiRobot) {

  fun enableGofmtOnReformat() {
    step("Enable gofmt on Reformat Code") {
      driver.withContext {
        ui.ideFrame {
          openSettingsDialog()
          settingsDialog {
            openTreeSettingsSection("Editor", "Code Style", "Go", fullMatch = true)
            openGoCodeStyleOtherTab()
            val checkBox = gofmtOnReformatCheckBox()
            if (!checkBox.isSelected()) {
              checkBox.click()
            }
            button("OK").click()
          }
        }
      }
    }
  }

  fun reformatCode(file: String) {
    step("Invoke 'Reformat Code' from the file context menu") {
      driver.withContext {
        ui.ideFrame {
          leftToolWindowToolbar.projectButton.open()
          projectView {
            projectViewTree.rightClickPath(checkNotNull(project).getName(), *file.split('/').toTypedArray(), fullMatch = false)
          }
          popupMenu().select("Reformat Code")
        }
        confirmReformatFilesDialog()
      }
    }
  }

  private fun confirmReformatFilesDialog() {
    val reformatFilesDialog = ui.dialog(title = REFORMAT_FILES_DIALOG)
    val asksForOptions = runCatching {
      reformatFilesDialog.shouldBe("The '$REFORMAT_FILES_DIALOG' dialog is shown", present, APPEARANCE_TIMEOUT)
    }.isSuccess

    if (!asksForOptions) {
      check(!ui.isDialogOpened()) { "'Reformat Code' opened a dialog other than '$REFORMAT_FILES_DIALOG'" }
      return
    }

    reformatFilesDialog.okButton.click()
    reformatFilesDialog.shouldBe(
      "The '$REFORMAT_FILES_DIALOG' dialog is still open after 'OK'",
      notPresent,
      DIALOG_TIMEOUT,
    )
  }

  private companion object {
    const val REFORMAT_FILES_DIALOG = "Reformat Files"

    val APPEARANCE_TIMEOUT = 10.seconds

    val DIALOG_TIMEOUT = 30.seconds
  }
}
