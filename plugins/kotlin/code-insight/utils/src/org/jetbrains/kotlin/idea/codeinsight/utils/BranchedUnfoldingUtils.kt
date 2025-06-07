// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

object BranchedUnfoldingUtils {
    fun unfoldAssignmentToIf(assignment: KtBinaryExpression, moveCaretToOffset: (Int) -> Unit) {
        val op = assignment.operationReference.text
        val left = assignment.left!!
        val ifExpression = assignment.right as KtIfExpression

        val newIfExpression = ifExpression.copied()

        val thenExpr = newIfExpression.then!!.lastBlockStatementOrThis()
        val elseExpr = newIfExpression.`else`!!.lastBlockStatementOrThis()

        val psiFactory = KtPsiFactory(assignment.project)
        thenExpr.replace(psiFactory.createExpressionByPattern("$0 $1 $2", left, op, thenExpr))
        elseExpr.replace(psiFactory.createExpressionByPattern("$0 $1 $2", left, op, elseExpr))

        val resultIf = assignment.replace(newIfExpression)

        resultIf.reformat()
        moveCaretToOffset(resultIf.textOffset)
    }

    fun unfoldAssignmentToWhen(assignment: KtBinaryExpression, moveCaretToOffset: (Int) -> Unit) {
        val op = assignment.operationReference.text
        val left = assignment.left!!
        val whenExpression = assignment.right as KtWhenExpression

        val newWhenExpression = whenExpression.copied()

        val psiFactory = KtPsiFactory(assignment.project)

        for (entry in newWhenExpression.entries) {
            val expr = entry.expression!!.lastBlockStatementOrThis()
            expr.replace(psiFactory.createExpressionByPattern("$0 $1 $2", left, op, expr))
        }

        val resultWhen = assignment.replace(newWhenExpression)

        resultWhen.reformat()
        moveCaretToOffset(resultWhen.textOffset)
    }
}
