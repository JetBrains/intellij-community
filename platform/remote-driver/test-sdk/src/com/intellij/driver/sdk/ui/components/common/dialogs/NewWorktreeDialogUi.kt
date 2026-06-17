package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.textField

fun Finder.newWorktreeDialog(action: NewWorktreeDialogUi.() -> Unit) {
  x("//div[@title='New Worktree']", NewWorktreeDialogUi::class.java).action()
}

class NewWorktreeDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Create & Open Worktree"

  val createButton: UiComponent
    get() = x("//div[@class='JButton' and @visible_text='$primaryButtonText']")

  fun branchComboBox(): JComboBoxUiComponent = comboBox()

  fun locationField(): JTextFieldUI = x { byClass("TextFieldWithBrowseButton") }.textField()

  fun setNewBranchName(name: String) {
    editor().text = name
  }

  fun clickCreate() {
    pressButton(primaryButtonText)
  }

  fun selectSourceBranch(branchName: String) {
    branchComboBox().apply {
      selectItem(listValues().first { it.trim().substringBefore(" ") == branchName })
    }
  }
}
