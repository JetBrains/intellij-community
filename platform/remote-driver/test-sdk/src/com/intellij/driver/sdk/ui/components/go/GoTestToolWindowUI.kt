package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData

fun Finder.goTestToolWindow(action: GoTestToolWindowUI.() -> Unit = {}) {
  x(GoTestToolWindowUI::class.java) {
    byClass("TestsConsoleViewImpl")
  }.apply(action)
}

class GoTestToolWindowUI(data: ComponentData) : GoConsoleToolWindowUI(data)
