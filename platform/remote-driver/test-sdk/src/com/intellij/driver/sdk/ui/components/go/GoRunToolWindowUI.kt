package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.goRunToolWindow(action: GoRunToolWindowUI.() -> Unit = {}) {
  x(GoRunToolWindowUI::class.java) { byClass("ConsoleViewImpl") }.apply(action)
}

class GoRunToolWindowUI(data: ComponentData) : UiComponent(data) {
  val editor: UiComponent
    get() = x { byAccessibleName("Editor") }

  fun getEditorText(): String = editor.getAllTexts().asString()

  fun containsText(text: String): Boolean = getEditorText().contains(text)
}
