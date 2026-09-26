// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.insertHandler

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement

internal class KotlinGradleConfigurationInsertHandler(val isPsiResolvable: Boolean) : InsertHandler<LookupElement> {
  private val parenthesesInsertHandler = ParenthesesInsertHandler.getInstance(
    /* hasParameters = */ true,
    /* spaceBeforeParentheses = */ false,
    /* spaceBetweenParentheses = */ false,
    /* insertRightParenthesis = */ true,
    /* allowParametersOnNextLine = */ false,
  )

  override fun handleInsert(
    context: InsertionContext,
    item: LookupElement,
  ) {
    if (!isPsiResolvable) {
      insertQuotesToWrapName(context)
    }
    parenthesesInsertHandler.handleInsert(context, item)
    AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
  }

  private fun insertQuotesToWrapName(context: InsertionContext) {
    val document = context.document
    val startOffset = context.startOffset
    document.insertString(context.tailOffset, "\"")
    document.insertString(startOffset, "\"")
  }
}
