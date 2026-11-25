package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.runToolWindow
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration

internal fun IdeaFrameUI.runConsoleTexts(): List<String> =
  runToolWindow().xx(JEditorUiComponent::class.java) { byAccessibleName("Editor") }.list().map { it.text }

internal fun IdeaFrameUI.shouldHaveRunOutput(vararg texts: String, timeout: Duration) {
  require(texts.isNotEmpty()) { "texts must not be empty" }
  var consoles = emptyList<String>()
  var missing = texts.toList()
  waitFor(
    message = "Run output reports ${texts.joinToString(prefix = "[", postfix = "]")}",
    timeout = timeout,
    errorMessage = { "Run output is missing $missing. Actual consoles:\n${consoles.joinToString("\n---\n")}" },
  ) {
    consoles = runConsoleTexts()
    missing = texts.filter { text -> consoles.none { it.contains(text) } }
    missing.isEmpty()
  }
}
