package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JRadioButtonUi
import com.intellij.driver.sdk.ui.components.elements.radioButton

fun Finder.gitResetDialog(action: GitResetDialogUi.() -> Unit): GitResetDialogUi {
  return x(GitResetDialogUi::class.java) { byTitle("Git Reset") }.apply(action)
}

enum class GitResetMode {
  SOFT, MIXED, HARD, KEEP,
}

class GitResetDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Reset"

  fun modeRadio(mode: GitResetMode): JRadioButtonUi = radioButton { byAccessibleName(mode.label) }

  fun selectMode(mode: GitResetMode) {
    modeRadio(mode).click()
  }

  fun confirm() {
    okButton.click()
  }

  fun cancel() {
    cancelButton.click()
  }

  private val GitResetMode.label: String
    get() = when (this) {
      GitResetMode.SOFT -> "Soft"
      GitResetMode.MIXED -> "Mixed"
      GitResetMode.HARD -> "Hard"
      GitResetMode.KEEP -> "Keep"
    }
}
