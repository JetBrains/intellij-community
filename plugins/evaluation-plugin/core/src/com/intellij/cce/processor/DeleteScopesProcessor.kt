package com.intellij.cce.processor

import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.core.CodeFragment

class DeleteScopesProcessor : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    addAction(DeleteRange(code.offset, code.offset + code.length))
  }
}