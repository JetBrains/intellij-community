// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.psi.psiUtil.siblings

object InvertIfConditionUtils {
    fun handleStandardCase(ifExpression: KtIfExpression, newCondition: KtExpression): KtIfExpression {
        val psiFactory = KtPsiFactory(ifExpression.project)

        val thenBranch = ifExpression.then!!
        val elseBranch = ifExpression.`else` ?: psiFactory.createEmptyBody()

        val newThen = if (elseBranch is KtIfExpression)
            psiFactory.createSingleStatementBlock(elseBranch)
        else
            elseBranch

        val newElse = if (thenBranch is KtBlockExpression && thenBranch.statements.isEmpty())
            null
        else
            thenBranch

        val conditionLineNumber = ifExpression.condition?.getLineNumber(false)
        val thenBranchLineNumber = thenBranch.getLineNumber(false)
        val elseKeywordLineNumber = ifExpression.elseKeyword?.getLineNumber()
        val afterCondition = if (newThen !is KtBlockExpression && elseKeywordLineNumber != elseBranch.getLineNumber(false)) "\n" else ""
        val beforeElse = if (newThen !is KtBlockExpression && conditionLineNumber != elseKeywordLineNumber) "\n" else " "
        val afterElse = if (newElse !is KtBlockExpression && conditionLineNumber != thenBranchLineNumber) "\n" else " "

        val newIf = if (newElse == null) {
            psiFactory.createExpressionByPattern("if ($0)$afterCondition$1", newCondition, newThen)
        } else {
            psiFactory.createExpressionByPattern("if ($0)$afterCondition$1${beforeElse}else$afterElse$2", newCondition, newThen, newElse)
        } as KtIfExpression

        return ifExpression.replaced(newIf)
    }

    fun isEmptyReturn(statement: KtExpression) =
        statement is KtReturnExpression && statement.returnedExpression == null && statement.labeledExpression == null

    fun copyThenBranchAfter(ifExpression: KtIfExpression): KtIfExpression {
        val psiFactory = KtPsiFactory(ifExpression.project)
        val thenBranch = ifExpression.then ?: return ifExpression

        val parent = ifExpression.parent
        if (parent !is KtBlockExpression) {
            assert(parent is KtContainerNode)
            val block = psiFactory.createEmptyBody()
            block.addAfter(ifExpression, block.lBrace)
            val newBlock = ifExpression.replaced(block)
            val newIf = newBlock.statements.single() as KtIfExpression
            return copyThenBranchAfter(newIf)
        }

        if (thenBranch is KtBlockExpression) {
            (thenBranch.statements.lastOrNull() as? KtContinueExpression)?.delete()
            val range = thenBranch.contentRange()
            if (!range.isEmpty) {
                parent.addRangeAfter(range.first, range.last, ifExpression)
                parent.addAfter(psiFactory.createNewLine(), ifExpression)
            }
        } else if (thenBranch !is KtContinueExpression) {
            parent.addAfter(thenBranch, ifExpression)
            parent.addAfter(psiFactory.createNewLine(), ifExpression)
        }
        return ifExpression
    }

    fun parentBlockRBrace(element: KtIfExpression): PsiElement? = (element.parent as? KtBlockExpression)?.rBrace

    fun KtIfExpression.nextEolCommentOnSameLine(): PsiElement? = getLineNumber(false).let { lastLineNumber ->
        siblings(withItself = false)
            .takeWhile { it.getLineNumber() == lastLineNumber }
            .firstOrNull { it is PsiComment && it.node.elementType == KtTokens.EOL_COMMENT }
    }
}
