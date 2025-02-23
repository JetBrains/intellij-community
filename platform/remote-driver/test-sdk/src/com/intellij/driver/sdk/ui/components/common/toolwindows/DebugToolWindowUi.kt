package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.button


class DebugToolWindowUi(data: ComponentData) : UiComponent(data) {
  val consoleTab get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Console")) }
  val threadsAndVariablesTab get() = x { and(byClass("SimpleColoredComponent"), byAccessibleName("Threads & Variables")) }

  val consoleView get() = x { byType("com.intellij.execution.impl.ConsoleViewImpl") }
  val resumeButton get() = button { byAccessibleName("Resume Program") }
  val stepOverButton get() = button { byAccessibleName("Step Over") }
  val stepOutButton get() = button { byAccessibleName("Step Out") }
  val stepIntoButton get() = button { byAccessibleName("Step Into") }
}

fun IdeaFrameUI.debugToolWindow(func: DebugToolWindowUi.() -> Unit = {}) =
  x(DebugToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Threads & Variables")) }.apply(func)

