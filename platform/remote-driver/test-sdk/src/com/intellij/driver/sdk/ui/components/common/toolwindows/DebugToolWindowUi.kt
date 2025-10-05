package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.button

fun IdeaFrameUI.debugToolWindow(func: DebugToolWindowUi.() -> Unit = {}) =
  x(DebugToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Threads & Variables")) }.apply(func)

class DebugToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  val consoleTab get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Console")) }
  val threadsAndVariablesTab get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Threads & Variables")) }
  val debuggerConsoleTab get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Debugger Console")) }
  val memoryView get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Memory View")) }
  val consoleView get() = x { byType("com.intellij.execution.impl.ConsoleViewImpl") }
  val resumeButton get() = button { byAccessibleName("Resume Program") }
  val stopButton get() = x("//div[@myicon='stop.svg']")
  val stepOverButton get() = button { byAccessibleName("Step Over") }
  val stepOutButton get() = button { byAccessibleName("Step Out") }
  val stepIntoButton get() = button { byAccessibleName("Step Into") }
  val jbRunnerTabs get() = x { byType("com.intellij.execution.ui.layout.impl.JBRunnerTabs") }
}

