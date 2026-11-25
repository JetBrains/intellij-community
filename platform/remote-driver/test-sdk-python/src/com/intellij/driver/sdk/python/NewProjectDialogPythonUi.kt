package com.intellij.driver.sdk.python

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.shouldBe
import kotlin.time.Duration.Companion.seconds

val NewProjectDialogUI.createMainPyCheckbox: JCheckBoxUi get() = checkBox { byText("Create a welcome script") }

val NewProjectDialogUI.installMinicondaLink: UiComponent get() = x("//div[@text='Install Miniconda']")

val NewProjectDialogUI.projectVenvButton: UiComponent get() = x { byAccessibleName("Project venv") }

/** Waits for the list of the base interpreters and returns its items. */
fun NewProjectDialogUI.getBasePythonInterpreters(): List<String> {
  val interpreterSelector = comboBox { byClass("PythonInterpreterComboBox") }
  interpreterSelector.waitFound().shouldBe(
    message = "Wait for interpreter list to appear",
    timeout = 5.seconds,
    condition = { listValues().isNotEmpty() }
  )
  return interpreterSelector.listValues()
}
