package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.button

fun IdeaFrameUI.runToolWindow(func: RunToolWindow.() -> Unit = {}): RunToolWindow =
  x(RunToolWindow::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Run")) }.apply(func)


class RunToolWindow(data: ComponentData) : ToolWindowUiComponent(data) {
  val rerunFailedTestsButton: JButtonUiComponent = button { byAccessibleName("Rerun Failed Tests") }
  val rerunAutomaticallyButton: JButtonUiComponent = button { byAccessibleName("Rerun Automatically") }
  fun getRerunButton(accessibleName: String): JButtonUiComponent = button { contains(byAccessibleName(accessibleName)) }
}