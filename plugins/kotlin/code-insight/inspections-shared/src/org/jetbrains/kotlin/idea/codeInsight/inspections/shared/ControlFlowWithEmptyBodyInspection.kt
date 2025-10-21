// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.isCallingAnyOf
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ControlFlowWithEmptyBodyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            val thenBranch = expression.then
            val elseBranch = expression.`else`

            val thenIsEmpty = thenBranch.isEmptyBodyOrNull()
            val elseIsEmpty = elseBranch.isEmptyBodyOrNull()


            val isUsedAsExpression by lazy {
                analyze(expression) { expression.isUsedAsExpression }
            }

            expression.ifKeyword
                .takeIf { thenIsEmpty && (elseIsEmpty || thenBranch.hasNoComments()) && !isUsedAsExpression }
                ?.let { holder.registerProblem(expression, it) }

            expression.elseKeyword
                ?.takeIf { elseIsEmpty && (thenIsEmpty || elseBranch.hasNoComments()) && !isUsedAsExpression }
                ?.let { holder.registerProblem(expression, it) }
        }

        override fun visitWhenExpression(expression: KtWhenExpression) {
            if (expression.entries.isNotEmpty()) return
            holder.registerProblem(expression, expression.whenKeyword)
        }

        override fun visitForExpression(expression: KtForExpression) {
            if (expression.body.isEmptyBodyOrNull()) {
                holder.registerProblem(expression, expression.forKeyword)
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression) {
            val keyword = expression.allChildren.firstOrNull { it.node.elementType == KtTokens.WHILE_KEYWORD } ?: return
            val body = expression.body
            if (body.hasNoComments() && body.isEmptyBodyOrNull()) {
                holder.registerProblem(expression, keyword)
            }
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
            val keyword = expression.allChildren.firstOrNull { it.node.elementType == KtTokens.DO_KEYWORD } ?: return
            val body = expression.body
            if (body.hasNoComments() && body.isEmptyBodyOrNull()) {
                holder.registerProblem(expression, keyword)
            }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            val callee = expression.calleeExpression ?: return
            val body = when (val argument = expression.valueArguments.singleOrNull()?.getArgumentExpression()) {
                is KtLambdaExpression -> argument.bodyExpression
                is KtNamedFunction -> argument.bodyBlockExpression
                else -> return
            }

            if (!body.isEmptyBodyOrNull()) return

            val isCallingControlFlowFunctions = analyze(expression) {
                expression.isCallingAnyOf(StandardKotlinNames.also)
            }
            if (!isCallingControlFlowFunctions) return

            holder.registerProblem(expression, callee)
        }
    }

    private fun KtExpression?.isEmptyBodyOrNull(): Boolean = this?.isEmptyBody() ?: true

    private fun KtExpression.isEmptyBody(): Boolean = this is KtBlockExpression && statements.isEmpty()

    private fun KtExpression?.hasNoComments(): Boolean = this?.anyDescendantOfType<PsiComment>() != true

    private fun ProblemsHolder.registerProblem(expression: KtExpression, keyword: PsiElement) {
        val keywordText = if (expression is KtDoWhileExpression) "do while" else keyword.text
        registerProblem(
            expression,
            keyword.textRange.shiftLeft(expression.startOffset),
            KotlinBundle.message("0.has.empty.body", keywordText)
        )
    }
}