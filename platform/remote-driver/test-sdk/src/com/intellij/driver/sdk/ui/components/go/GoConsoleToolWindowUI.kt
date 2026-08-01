package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import com.intellij.driver.sdk.ui.should
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Common part of the Go Run and Go Test console tool windows.
 */
abstract class GoConsoleToolWindowUI(data: ComponentData) : ToolWindowUiComponent(data) {
  val editor: JEditorUiComponent = x(JEditorUiComponent::class.java) { byAccessibleName("Editor") }

  fun getEditorText(): String = editor.text

  fun shouldContainTexts(vararg texts: String, timeout: Duration = 1.minutes) {
    require(texts.isNotEmpty()) { "texts must not be empty" }
    step("Verify console output contains ${texts.joinToString(prefix = "[", postfix = "]")}") {
      var missing = texts.toList()
      var output = ""
      should(
        message = "Console output contains ${texts.joinToString(prefix = "[", postfix = "]")}",
        timeout = timeout,
        errorMessage = { "Console output doesn't contain $missing. Actual output:\n$output" },
      ) {
        output = getEditorText()
        missing = texts.filterNot { it in output }
        missing.isEmpty()
      }
    }
  }
}
