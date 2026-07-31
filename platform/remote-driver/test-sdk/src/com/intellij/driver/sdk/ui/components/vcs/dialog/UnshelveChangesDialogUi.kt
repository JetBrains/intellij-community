package com.intellij.driver.sdk.ui.components.vcs.dialog

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.checkBox

fun Finder.unshelveChangesDialog(action: UnshelveChangesDialogUi.() -> Unit = {}): UnshelveChangesDialogUi =
  x("//div[@title='Unshelve Changes']", UnshelveChangesDialogUi::class.java).apply(action)

class UnshelveChangesDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Unshelve Changes"

  val unshelveButton: UiComponent
    get() = x("//div[@class='JButton' and @visible_text='$primaryButtonText']")

  fun typeChangelistName(name: String) {
    keyboard { typeText(name) }
  }

  val setActiveCheckBox: JCheckBoxUi get() = checkBox { contains(byAccessibleName("Set active")) }
  val trackContextCheckBox: JCheckBoxUi get() = checkBox { contains(byAccessibleName("Track context")) }
  val removeAppliedFilesCheckBox: JCheckBoxUi get() = checkBox { contains(byAccessibleName("Remove successfully applied")) }
}
