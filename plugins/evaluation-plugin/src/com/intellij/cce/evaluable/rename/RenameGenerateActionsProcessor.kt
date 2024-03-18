// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename

import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.actions.Rename
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor

class RenameGenerateActionsProcessor(
  private val strategy: RenameStrategy
) : GenerateActionsProcessor() {

  override fun process(code: CodeFragment) {
    for (token in code.getChildren()) {
      processToken(token as CodeToken)
    }
  }

  private fun processToken(token: CodeToken) {
    if (checkFilters(token)) {
      addAction(MoveCaret(token.offset))
      addAction(Rename(token.offset, strategy.placeholderName))
      addAction(CallFeature(token.text, token.offset, token.properties))
      addAction(Rename(token.offset, token.text))
    }
  }

  private fun checkFilters(token: CodeToken) = strategy.filters.all { it.value.shouldEvaluate(token) }
}
