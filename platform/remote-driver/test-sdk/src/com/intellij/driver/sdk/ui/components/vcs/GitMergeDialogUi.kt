package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor

fun Finder.gitMergeDialog(action: GitMergeDialogUi.() -> Unit = {}): GitMergeDialogUi {
  return x(GitMergeDialogUi::class.java) { byClass("MyDialog") and contains(byTitle("Merge")) }.apply(action)
}

class GitMergeDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Merge"

  private val branchComboBox: JComboBoxUiComponent
    get() = comboBox { byClass("ComboBoxWithAutoCompletion") }

  val commitMessageField: UiComponent
    get() = x { byClass("JBTextArea") }

  val modifyOptionsLink: UiComponent
    get() = x { byClass("DropDownLink") and byVisibleText("Modify options") }

  fun selectBranch(branchName: String) {
    branchComboBox.selectItemContains(branchName)
  }

  fun confirm() {
    okButton.click()
    waitFor("Merge dialog to close") { notPresent() }
  }

  private fun getActiveOptions(): List<UiComponent> {
    return xx { byClass("OptionButton") }.list()
  }

  fun deselectAllOptions() {
    getActiveOptions().forEach { it.x { byAccessibleName("Remove option") }.click() }
  }

  fun getActiveOptionTexts(): List<String> {
    return getActiveOptions().map { it.getAllTexts().joinToString(" ") { text -> text.text } }
  }

  fun selectOption(optionFlag: String) {
    modifyOptionsLink.click()
    driver.ui.popup().list().clickItem(optionFlag, fullMatch = false)
    driver.ui.keyboard { escape() }
  }

  fun cancel() {
    cancelButton.click()
  }
}
