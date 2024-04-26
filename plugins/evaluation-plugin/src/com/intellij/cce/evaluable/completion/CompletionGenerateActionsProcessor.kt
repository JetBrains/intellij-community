// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.actions.MoveCaret
import com.intellij.cce.actions.PrintText
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.processor.TextScopes


class CompletionGenerateActionsProcessor(private val strategy: CompletionStrategy) : GenerateActionsProcessor() {
  private var previousTextStart = 0
  var text = ""

  override fun process(code: CodeFragment) {
    if (strategy.context == CompletionContext.PREVIOUS) {
      addAction(DeleteRange(code.offset, code.offset + code.length))
    }

    if (strategy.context == CompletionContext.ALL) {
      for (token in code.getChildren()) {
        processToken(token as CodeToken)
      }
      return
    }
    previousTextStart = code.offset
    text = code.text


    for (token in code.getChildren()) {
      processToken(token as CodeToken)
    }

    if (previousTextStart < code.offset + code.length) {

      val remainingText = this.text.substring(IntRange(previousTextStart, code.offset + code.length - 1))

      val scopes = TextScopes(remainingText)
      if (scopes.closedCount != 0) {
        addAction(DeleteRange(previousTextStart, previousTextStart + scopes.closedCount))
      }

      addAction(MoveCaret(previousTextStart))
      addAction(PrintText(remainingText))
    }
  }

  private fun processToken(token: CodeToken) {
    if (!checkFilters(token)) return

    when (strategy.context) {
      CompletionContext.ALL -> prepareAllContext(token)
      CompletionContext.PREVIOUS -> preparePreviousContext(token)
    }

    addAction(CallFeature(token.text, token.offset, token.properties))
  }

  private fun prepareAllContext(token: CodeToken) {
    addAction(DeleteRange(token.offset, token.offset + token.text.length))
    addAction(MoveCaret(token.offset))
  }

  private fun preparePreviousContext(token: CodeToken) {
    if (previousTextStart <= token.offset) {
      val previousText = text.substring(IntRange(previousTextStart, token.offset - 1))

      val scopes = TextScopes(previousText)

      if (scopes.closedCount != 0) {
        addAction(DeleteRange(previousTextStart, previousTextStart + scopes.closedCount))
      }

      addAction(MoveCaret(previousTextStart))
      addAction(PrintText(previousText))

      if (scopes.reversedOpened.isNotEmpty()) {
        addAction(PrintText(scopes.reversedOpened))
      }

      previousTextStart = token.offset + token.text.length
      addAction(MoveCaret(token.offset))
    }
  }

  private fun checkFilters(token: CodeToken) = strategy.filters.all { it.value.shouldEvaluate(token) }
}