// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

abstract class KotlinExpressionSurrounder : Surrounder {
  override fun isApplicable(elements: Array<PsiElement>): Boolean {
    if (elements.size != 1 || elements[0] !is KtExpression) {
      return false
    }
    val expression = elements[0] as KtExpression
    return if (expression is KtCallExpression && expression.getParent() is KtQualifiedExpression) {
      false
    }
    else isApplicable(expression)
  }

  @OptIn(KtAllowAnalysisOnEdt::class)
  protected open fun isApplicable(expression: KtExpression): Boolean {
      allowAnalysisOnEdt {
          return analyze(expression) {
              val type = expression.getKtType()
              if (type == null || type is KtErrorType || type.isUnit && isApplicableToStatements) {
                  false
              }
              else isApplicableToStatements || expression.isUsedAsExpression()
          }
      }
  }

  protected open val isApplicableToStatements: Boolean
    get() = true

  override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
    assert(elements.size == 1) { "KotlinExpressionSurrounder should be applicable only for 1 expression: " + elements.size }
    return surroundExpression(project, editor, elements[0] as KtExpression)
  }

  protected abstract fun surroundExpression(project: Project, editor: Editor, expression: KtExpression): TextRange?
}
