// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor

class RenameGenerateActionsProcessor(
  private val strategy: RenameStrategy
) : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    actions {
      for (token in code.getChildren()) {
        check(token is CodeToken)
        if (checkFilters(token)) {
          session {
            moveCaret(token.offset)
            rename(token.offset, strategy.placeholderName)
            callFeature(token.text, token.offset, token.properties)
            rename(token.offset, token.text)
          }
        }
      }
    }
  }

  private fun checkFilters(token: CodeToken) = strategy.filters.all { it.value.shouldEvaluate(token) }
}
