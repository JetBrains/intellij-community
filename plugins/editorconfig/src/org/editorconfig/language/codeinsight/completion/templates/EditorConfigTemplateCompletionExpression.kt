// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result

class EditorConfigTemplateCompletionExpression : Expression() {
  override fun calculateResult(context: ExpressionContext?): Result? = null
  override fun calculateQuickResult(context: ExpressionContext?): Result? = null
  override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement> {
    invokeCompletion(context)
    return emptyArray()
  }

  private fun invokeCompletion(context: ExpressionContext) {
    val project = context.project ?: return
    val editor = context.editor ?: return
    AutoPopupController.runTransactionWithEverythingCommitted(project) {
      CompletionAutoPopupHandler.invokeCompletion(CompletionType.BASIC, true, project, editor, 0, false)
    }
  }
}
