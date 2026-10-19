package com.intellij.driver.sdk.ui.components.common.editor

import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.withRetries
import java.awt.event.KeyEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The descriptions of the highlights of this editor's document. An empty list when the highlights cannot be read. */
fun JEditorUiComponent.highlightDescriptions(): List<String> =
  runCatching { driver.getHighlights(document).mapNotNull { it.getDescription() } }.getOrDefault(emptyList())

/**
 * Presses Alt+Enter at the 1-based [line] and [column] and returns the text of the intention popup item whose text starts with [itemPrefix].
 *
 * Tries [times] times and presses Escape between the attempts: a quick-fix can come into the list later than its highlight.
 */
fun JEditorUiComponent.intentionItemWithRetries(
  line: Int,
  column: Int,
  itemPrefix: String,
  times: Int = 3,
  timeout: Duration = 10.seconds,
): UiText =
  withRetries("The intention popup offers '$itemPrefix'", times = times, onError = { keyboard { escape() } }) {
    goToPosition(line, column)
    keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_ENTER) }
    driver.ui.popup().waitOneText(timeout = timeout) { it.text.startsWith(itemPrefix) }
  }
