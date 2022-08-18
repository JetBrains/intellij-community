package com.intellij.cce.processor

import com.intellij.cce.actions.CompletionGolfSession
import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.core.*

class CompletionGolfProcessor : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    code.getChildren().forEach {
      addActions(it)
    }
  }

  private fun addActions(token: CodeToken) {
    if (token.text.isNotEmpty()) {
      val nodeProperties = SimpleTokenProperties.create(TypeProperty.LINE, SymbolLocation.UNKNOWN) {}
      addAction(DeleteRange(token.offset, token.offset + token.length))
      addAction(MoveCaret(token.offset))
      addAction(CompletionGolfSession(token.text, nodeProperties))
    }
  }
}
