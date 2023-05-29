// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.ex.lineNumber

import com.intellij.openapi.editor.impl.RelativeLineHelper.getRelativeLine
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LineNumberConverter
import kotlin.math.abs

object HybridLineNumberConverter : LineNumberConverter {
  override fun shouldRepaintOnCaretMovement(): Boolean {
    return true
  }

  override fun convert(editor: Editor, lineNumber: Int): Int {
    val caretLine = editor.caretModel.logicalPosition.line
    return if (lineNumber - 1 == caretLine) {
      lineNumber
    }
    else abs(getRelativeLine(editor, caretLine, lineNumber - 1))
  }

  override fun getMaxLineNumber(editor: Editor): Int {
    return editor.document.lineCount
  }
}