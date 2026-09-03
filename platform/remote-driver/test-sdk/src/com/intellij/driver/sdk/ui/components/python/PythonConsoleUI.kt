package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton


fun IdeaFrameUI.pythonConsole(func: PythonConsoleUi.() -> Unit = {}) =
  x(PythonConsoleUi::class.java) {
    componentWithChild(byClass("InternalDecoratorImpl"), byClass("PythonConsoleView"))
  }.apply(func)

class PythonConsoleUi(data: ComponentData) : UiComponent(data) {
  val header = x { byAccessibleName("Tool Window Header") }
  val content = x { byClass("PythonConsoleView") }

  /**
   * The `Console.Execute` button of the console toolbar.
   *
   * `performAction` clicks the button on the EDT. So it needs no key event and no focus owner.
   */
  val executeButton = actionButton {
    and(byClass("ActionButton"), byAccessibleName("Execute Current Statement in Console"))
  }
  val consoleEditor: UiComponent = x("//div[@class='PythonConsoleView']//div[@accessiblename='Editor' and @class='EditorComponentImpl']", "Python console editor")
  val prompt = xx(JEditorUiComponent::class.java) { byClass("EditorComponentImpl") }.list().last()
  fun promptIsReady(): Boolean {
    return xx { byClass("JPanel") }.list().size > 1
  }
}
