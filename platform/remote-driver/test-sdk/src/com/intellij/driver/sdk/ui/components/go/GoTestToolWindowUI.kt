package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.goTestToolWindow(action: GoTestToolWindowUI.() -> Unit = {}) {
  x(GoTestToolWindowUI::class.java) {
    byClass("InternalDecoratorImpl")
  }.apply(action)
}

class GoTestToolWindowUI(data: ComponentData) : UiComponent(data) {
  val editor: UiComponent
    get() = x { byAccessibleName("Editor") }

  val scrollDownButton: UiComponent
    get() = x { byAttribute("myicon", "scrollDown.svg") }

  fun getEditorText(): String = editor.getAllTexts().asString()

  fun containsText(text: String): Boolean = getEditorText().contains(text)
}
