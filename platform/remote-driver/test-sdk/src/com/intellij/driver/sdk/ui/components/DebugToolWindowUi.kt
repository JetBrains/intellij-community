package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Locators

class DebugToolWindowUi(data: ComponentData): UiComponent(data) {
  val consoleTab get() = x(Locators.byClassAndAccessibleName("SimpleColoredComponent", "Console"))

  val consoleView get() = x(Locators.byType("com.intellij.execution.impl.ConsoleViewImpl"))
}

fun IdeaFrameUI.debugToolWindow(func: DebugToolWindowUi.() -> Unit = {}) =
  x(Locators.componentWithChild(Locators.byClass("InternalDecoratorImpl"), Locators.byAccessibleName("Threads & Variables")), DebugToolWindowUi::class.java)
    .apply(func)

