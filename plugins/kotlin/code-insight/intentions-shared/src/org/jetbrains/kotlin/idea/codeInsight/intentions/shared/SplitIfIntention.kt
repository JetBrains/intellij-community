// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExitStatement
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.util.match

internal class SplitIfIntention :
    KotlinApplicableModCommandAction.Simple<KtExpression>(KtExpression::class) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val operator = when (element) {
            is KtIfExpression -> getFirstValidOperator(element)!!
            else -> element as KtOperationReferenceExpression
        }

        val ifExpression = operator.getNonStrictParentOfType<KtIfExpression>()

        val commentSaver = CommentSaver(ifExpression!!)

        val expression = operator.parent as KtBinaryExpression
        val rightExpression = KtPsiUtil.safeDeparenthesize(getRight(expression, ifExpression.condition!!, commentSaver))
        val leftExpression = KtPsiUtil.safeDeparenthesize(expression.left!!)
        val thenBranch = ifExpression.then!!
        val elseBranch = ifExpression.`else`

        val psiFactory = KtPsiFactory(element.project)

        val innerIf = psiFactory.createIf(rightExpression, thenBranch, elseBranch)

        val newIf = when (operator.getReferencedNameElementType()) {
            KtTokens.ANDAND -> psiFactory.createIf(leftExpression, psiFactory.createSingleStatementBlock(innerIf), elseBranch)

            KtTokens.OROR -> {
                val container = ifExpression.parent

                // special case
                if (container is KtBlockExpression && elseBranch == null && thenBranch.lastBlockStatementOrThis().isExitStatement()) {
                    val secondIf = container.addAfter(innerIf, ifExpression)
                    container.addAfter(psiFactory.createNewLine(), ifExpression)
                    val firstIf = ifExpression.replace(psiFactory.createIf(leftExpression, thenBranch))
                    commentSaver.restore(PsiChildRange(firstIf, secondIf))
                    return
                } else {
                    psiFactory.createIf(leftExpression, thenBranch, innerIf)
                }
            }

            else -> return
        }

        val result = ifExpression.replace(newIf)
        commentSaver.restore(result)
    }

    override fun getFamilyName(): String = KotlinBundle.message("split.if.into.two")

    override fun getApplicableRanges(element: KtExpression): List<TextRange> = when (element) {
        is KtIfExpression -> ApplicabilityRanges.ifKeyword(element)
        else -> ApplicabilityRange.self(element)
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean = when (element) {
        is KtOperationReferenceExpression -> isOperatorValid(element)
        is KtIfExpression -> getFirstValidOperator(element) != null
        else -> false
    }

    private fun isOperatorValid(element: KtOperationReferenceExpression): Boolean {
        val operator = element.getReferencedNameElementType()
        if (operator != KtTokens.ANDAND && operator != KtTokens.OROR) return false

        var expression = element.parent as? KtBinaryExpression ?: return false

        if (expression.right == null || expression.left == null) return false

        while (true) {
            expression = expression.parent as? KtBinaryExpression ?: break
            if (expression.operationToken != operator) return false
        }

        val ifExpression = expression.parents.match(KtContainerNode::class, last = KtIfExpression::class) ?: return false

        if (ifExpression.condition == null) return false
        if (!PsiTreeUtil.isAncestor(ifExpression.condition, element, false)) return false

        if (ifExpression.then == null) return false

        return true
    }

    private fun getFirstValidOperator(element: KtIfExpression): KtOperationReferenceExpression? {
        val condition = element.condition ?: return null
        return PsiTreeUtil.findChildrenOfType(condition, KtOperationReferenceExpression::class.java)
            .firstOrNull { isOperatorValid(it) }
    }

    private fun getRight(element: KtBinaryExpression, condition: KtExpression, commentSaver: CommentSaver): KtExpression {
        //gets the textOffset of the right side of the JetBinaryExpression in context to condition
        val conditionRange = condition.textRange
        val startOffset = element.right!!.startOffset - conditionRange.startOffset
        val endOffset = conditionRange.length
        val rightString = condition.text.substring(startOffset, endOffset)

        val expression = KtPsiFactory(element.project).createExpression(rightString)
        commentSaver.elementCreatedByText(expression, condition, TextRange(startOffset, endOffset))
        return expression
    }

}
