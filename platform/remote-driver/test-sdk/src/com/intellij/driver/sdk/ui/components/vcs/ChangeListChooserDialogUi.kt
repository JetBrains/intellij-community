package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi

fun Finder.selectTargetChangelistDialog(): ChangeListChooserDialogUi {
  return x(ChangeListChooserDialogUi::class.java) { byClass("MyDialog") and byTitle("Select Target Changelist") }
}

class ChangeListChooserDialogUi(data: ComponentData) : DialogUiComponent(data) {

  override val primaryButtonText: String = "OK"
  override val cancelButtonText: String = "Cancel"

  private val editors = xx(JEditorUiComponent::class.java) { byClass("EditorComponentImpl") }.list()

  /** First editor in the dialog: the Name field (inside the existing-changelists combo box). */
  val nameField: JEditorUiComponent = editors.first()

  /** The second editor in the dialog: the multi-line Comment / Description field. */
  val commentField: JEditorUiComponent = editors[1]

  val setActiveCheckbox: JCheckBoxUi = x(JCheckBoxUi::class.java) { byAccessibleName("Set active") }

  fun confirm() {
    okButton.click()
  }

  fun cancel() {
    cancelButton.click()
  }
}
