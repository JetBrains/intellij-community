package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.pythonEditorFloatingToolbar(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='EditorFloatingToolbar']", PythonEditorFloatingToolbarUi::class.java)

class PythonEditorFloatingToolbarUi(data: ComponentData) : UiComponent(data) {
  val poetryUpdateButton = x { byAccessibleName("Poetry Update") }
  val poetryLockButton = x { byAccessibleName("Poetry Lock") }
}
