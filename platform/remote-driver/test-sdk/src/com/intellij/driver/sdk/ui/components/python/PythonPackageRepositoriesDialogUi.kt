package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Package repository management moved from the standalone "Python Package Repositories" dialog into
 * the Settings dialog "Python | Package Repositories" page (PY-89838). This component targets that
 * Settings page; open it via the Python Packages tool window Options menu -> "Repositories...".
 */
fun IdeaFrameUI.pythonPackageRepositoriesDialog(func: PythonPackageRepositoriesDialogUi.() -> Unit = {}) =
  x(PythonPackageRepositoriesDialogUi::class.java) {
    and(or(byType(JDialog::class.java), byType(JFrame::class.java)), byAccessibleName("Settings"))
  }.apply(func)

class PythonPackageRepositoriesDialogUi(data: ComponentData) : UiComponent(data) {
  val addButton = x { and(byClass("ActionButton"), byAccessibleName("Add")) }
  val removeButton = x { and(byClass("ActionButton"), byAccessibleName("Delete")) }
  val okButton = x { byAccessibleName("OK") }
  val applyButton = x { byAccessibleName("Apply") }
  val cancelButton = x { byAccessibleName("Cancel") }

  val nameField = x("//div[@accessiblename='Name:' and @class='JBTextField']")
  val urlField = x("//div[@accessiblename='Repository URL:' and @class='JBTextField']")
}
