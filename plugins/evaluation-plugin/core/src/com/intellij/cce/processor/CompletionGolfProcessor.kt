package com.intellij.cce.processor

import com.intellij.cce.actions.CompletionGolfSession
import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.actions.TextRange
import com.intellij.cce.core.*

class CompletionGolfProcessor : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    code.getChildren().forEach {
      addActions(it as CodeLine)
    }
  }

  private fun addActions(line: CodeLine) {
    if (line.text.isNotEmpty()) {
      addAction(MoveCaret(line.offset))
      addAction(CompletionGolfSession(line.text, line.getChildren().map { TextRange(it.offset, it.offset + it.text.length) }))
    }
  }
}
