package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

enum class KotlinNotebookSessionRunMode(val title: String) {
  SEPARATE_PROCESS("Run in Separate Process"),
  IDE_PROCESS("Run in IDE Process"),
  ATTACHED_PROCESS("Attach to Running Kernel"),
}

class RunModeSelectorComboBox(data: ComponentData) : UiComponent(data) {
  private val actionButton: ComboBoxActionButton = ComboBoxActionButton(data)

  fun selectRunMode(mode: KotlinNotebookSessionRunMode) {
    actionButton.selectItem(itemText = mode.title)
  }
}
