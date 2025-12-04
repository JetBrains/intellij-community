package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.waitNotNull
import kotlin.math.absoluteValue

enum class KotlinNotebookSessionRunMode(val title: String) {
  SEPARATE_PROCESS("Run in Separate Process"),
  IDE_PROCESS("Run in IDE Process"),
  ATTACHED_PROCESS("Attach to Running Kernel"),
}

class RunModeSelectorComboBoxUiComponent(data: ComponentData): UiComponent(data) {
  private val currentRunMode: KotlinNotebookSessionRunMode?
    get() {
      val allTexts = getAllTexts()
      return KotlinNotebookSessionRunMode.entries.find {
        allTexts.any { text -> text.text == it.title }
      }
    }

  fun selectRunMode(mode: KotlinNotebookSessionRunMode) {
    // it looks like due to some race condition,
    // sometimes the combobox is not updated after the mode change, so we need to retry a few times
    val repetitionCount = 5
    repeat(repetitionCount) { attempt ->
      val currentMode = waitNotNull { currentRunMode }
      if (currentMode == mode) return

      val diff = mode.ordinal - currentMode.ordinal

      click()
      keyboard {
        repeat(diff.absoluteValue) {
          if (diff > 0) down() else up()
        }
        enter()
      }

      if (getAllTexts().any { it.text == mode.title }) return

      if (attempt == repetitionCount - 1) {
        error("Failed to select run mode after $repetitionCount attempts: $mode")
      }
    }
  }
}
