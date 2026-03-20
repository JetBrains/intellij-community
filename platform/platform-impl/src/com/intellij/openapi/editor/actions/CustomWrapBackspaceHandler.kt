// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler

/**
 * Handles backspace behavior for custom wraps.
 *
 * When caret is visually **after** a custom wrap (on the wrapped line), backspace deletes the wrap
 * instead of deleting a character.
 *
 * When caret is visually **before** a custom wrap (at the end of the line before the wrap),
 * the normal backspace behavior applies and the wrap survives.
 */
internal class CustomWrapBackspaceHandler(private val originalHandler: EditorActionHandler) : EditorWriteActionHandler.ForEachCaret() {

  override fun executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext) {
    val wrapsAtCaret = editor.customWrapModel.getWrapsAtOffset(caret.offset)
    if (wrapsAtCaret.isNotEmpty() && isCaretAfterWrap(editor, caret, caret.offset)) {
      // Caret is placed visually just after the custom wrap → delete the wrap
      wrapsAtCaret.forEach {
        editor.customWrapModel.removeWrap(it)
      }
      return
    }
    // Either no custom wrap at this position, or caret is before the wrap → normal backspace
    originalHandler.execute(editor, caret, dataContext)
  }

  /**
   * Determines if the caret is visually positioned after the custom wrap.
   */
  private fun isCaretAfterWrap(editor: Editor, caret: Caret, wrapOffset: Int): Boolean {
    val caretVisualLine = caret.visualPosition.line
    val visualLineAfterWrap = editor.offsetToVisualLine(wrapOffset, false)
    return caretVisualLine == visualLineAfterWrap
  }

  override fun isEnabledForCaret(
    editor: Editor,
    caret: Caret,
    dataContext: DataContext?,
  ): Boolean {
    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}
