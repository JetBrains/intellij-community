package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.pythonPackageRepositoriesDialog(func: PythonPackageRepositoriesDialogUi.() -> Unit = {}) =
  x(PythonPackageRepositoriesDialogUi::class.java) { byTitle("Python Package Repositories") }.apply(func)

class PythonPackageRepositoriesDialogUi(data: ComponentData) : UiComponent(data) {
  val addButton = x { byAccessibleName("Add") }
  val removeButton = x { byAccessibleName("Remove") }
  val okButton = x { byAccessibleName("OK") }
  val applyButton = x { byAccessibleName("Apply") }
  val cancelButton = x { byAccessibleName("Cancel") }

  val nameField = x("//div[@accessiblename='Name:' and @class='JTextField']")
  val urlField = x("//div[@accessiblename='Repository URL:' and @class='JBTextField']")
}
