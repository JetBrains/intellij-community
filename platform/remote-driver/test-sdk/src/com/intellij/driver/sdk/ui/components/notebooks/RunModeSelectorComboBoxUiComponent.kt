package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
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
    val currentMode = currentRunMode
    if (currentMode == mode || currentMode == null) return

    val diff = mode.ordinal - currentMode.ordinal

    click()
    keyboard {
      repeat(diff.absoluteValue) {
        if (diff > 0) down() else up()
      }
      enter()
    }
    waitForModeText(mode)
  }

  private fun waitForModeText(mode: KotlinNotebookSessionRunMode) {
    step("wait for mode text") {
      waitAnyTexts { it.text == mode.title }
    }
  }
}
