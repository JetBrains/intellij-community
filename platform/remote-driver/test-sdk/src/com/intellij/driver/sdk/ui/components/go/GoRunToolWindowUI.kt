package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData

fun Finder.goRunToolWindow(action: GoRunToolWindowUI.() -> Unit = {}) {
  x(GoRunToolWindowUI::class.java) { byClass("ConsoleViewImpl") }.apply(action)
}

class GoRunToolWindowUI(data: ComponentData) : GoConsoleToolWindowUI(data)
