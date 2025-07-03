package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent


fun IdeaFrameUI.pythonConsole(func: PythonConsoleUi.() -> Unit = {}) =
  x(PythonConsoleUi::class.java) {
    componentWithChild(byClass("InternalDecoratorImpl"), byClass("PythonConsoleView"))
  }.apply(func)

class PythonConsoleUi(data: ComponentData) : UiComponent(data) {
  val header = x { byAccessibleName("Tool Window Header") }
  val content = x { byClass("PythonConsoleView") }
  val prompt = xx(JEditorUiComponent::class.java) { byClass("EditorComponentImpl") }.list().last()
  fun promptIsReady(): Boolean {
    return xx { byClass("JPanel") }.list().size > 1
  }
}
