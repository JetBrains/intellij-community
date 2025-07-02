package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.concurrencyToolWindow(func: ConcurrencyToolWindowUi.() -> Unit = {}) =
  x(ConcurrencyToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Concurrent Activities Diagram")) }.apply(func)

class ConcurrencyToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  val stopProcessButton = x { and(byClass("ActionButton"), byAccessibleName("Stop process")) }
  val asyncioGraph = x { and(byClass("JLabel"), byAccessibleName("Asyncio graph")) }
}