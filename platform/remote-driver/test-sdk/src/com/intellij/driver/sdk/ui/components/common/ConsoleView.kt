package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.consoleView(@Language("xpath") xpath: String? = null, action: ConsoleView.() -> Unit) {
  x(xpath ?: "//div[@class='ConsoleViewImpl']", ConsoleView::class.java).action()
}

class ConsoleView(data: ComponentData) : UiComponent(data)