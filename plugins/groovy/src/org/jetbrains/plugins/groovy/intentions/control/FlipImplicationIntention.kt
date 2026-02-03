// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLNOT
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils

class FlipImplicationIntention : GrPsiUpdateIntention() {
  override fun processIntention(element: PsiElement, context: ActionContext, updater: ModPsiUpdater) {
    if (element !is GrBinaryExpression) return

    val lhsText = invertExpression(element.getLeftOperand())
    val rhsText = invertExpression(element.getRightOperand())

    if (lhsText == null || rhsText == null) return

    val newExpression = "$rhsText ==> $lhsText"
    PsiImplUtil.replaceExpression(newExpression, element)
  }

  override fun getElementPredicate(): PsiElementPredicate {
    return ImplicationPredicate()
  }

  private fun invertExpression(parenthesizedExpression: GrExpression?) : String? {
    if (parenthesizedExpression == null) return null
    val expression = ParenthesesUtils.unparenthesize(parenthesizedExpression)
    if (expression is GrUnaryExpression && expression.operationTokenType == mLNOT) {
      val operand = expression.getOperand() ?: return null
      val unparenthesized = ParenthesesUtils.unparenthesize(operand)
      return if (
        ParenthesesUtils.checkPrecedence(
          ParenthesesUtils.IMPL_PRECEDENCE,
          ParenthesesUtils.getPrecedence(unparenthesized)
        )) {
         unparenthesized.text
      } else {
        operand.text
      }
    }
    val expressionText = expression.getText()
    return if (
      ParenthesesUtils.checkPrecedence(
        ParenthesesUtils.getPrecedence(expression),
        ParenthesesUtils.TYPE_CAST_PRECEDENCE)
      ) {
       "!($expressionText)"
    }
    else {
      "!$expressionText"
    }
  }
}