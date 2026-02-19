// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.processor.TextScopes


class CompletionGenerateActionsProcessor(private val strategy: CompletionStrategy) : GenerateActionsProcessor() {
  private var previousTextStart = 0
  var text = ""

  override fun process(code: CodeFragment) {
    actions {
      if (strategy.context == CompletionContext.ALL) {
        for (token in code.getChildren()) {
          session {
            processToken(token as CodeToken)
          }
        }
      }
      else {
        // consider all completion invocations in this scenario as a single session
        // because they can't be called independently due to context preparation
        session {
          deleteRange(code.offset, code.offset + code.length)
          previousTextStart = code.offset
          text = code.text
          for (token in code.getChildren()) {
            processToken(token as CodeToken)
          }
          if (previousTextStart < code.offset + code.length) {
            val remainingText = text.substring(IntRange(previousTextStart, code.offset + code.length - 1))

            val scopes = TextScopes(remainingText)
            if (scopes.closedCount != 0) {
              deleteRange(previousTextStart, previousTextStart + scopes.closedCount)
            }

            moveCaret(previousTextStart)
            printText(remainingText)
          }
        }
      }
    }
  }

  private fun ActionsBuilder.SessionBuilder.processToken(token: CodeToken) {
    if (!checkFilters(token)) return

    when (strategy.context) {
      CompletionContext.ALL -> prepareAllContext(token)
      CompletionContext.PREVIOUS -> preparePreviousContext(token)
    }

    callFeature(token.text, token.offset, token.properties)
  }

  private fun ActionsBuilder.SessionBuilder.prepareAllContext(token: CodeToken) {
    deleteRange(token.offset, token.offset + token.text.length)
    moveCaret(token.offset)
  }

  private fun ActionsBuilder.SessionBuilder.preparePreviousContext(token: CodeToken) {
    if (previousTextStart <= token.offset) {
      val previousText = text.substring(IntRange(previousTextStart, token.offset - 1))

      val scopes = TextScopes(previousText)

      if (scopes.closedCount != 0) {
        deleteRange(previousTextStart, previousTextStart + scopes.closedCount)
      }

      moveCaret(previousTextStart)
      printText(previousText)

      if (scopes.reversedOpened.isNotEmpty()) {
        printText(scopes.reversedOpened)
      }

      previousTextStart = token.offset + token.text.length
      moveCaret(token.offset)
    }
  }

  private fun checkFilters(token: CodeToken) = strategy.filters.all { it.value.shouldEvaluate(token) }
}
