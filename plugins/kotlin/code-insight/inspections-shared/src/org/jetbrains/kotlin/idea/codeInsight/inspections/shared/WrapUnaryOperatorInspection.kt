// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceFirstReceiver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class WrapUnaryOperatorInspection : AbstractKotlinInspection() {

    private object Holder {
        val numberTypes: List<IElementType> = listOf(KtNodeTypes.INTEGER_CONSTANT, KtNodeTypes.FLOAT_CONSTANT)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return prefixExpressionVisitor { expression ->
            if (expression.operationToken.isUnaryMinusOrPlus()) {
                val baseExpression = expression.baseExpression
                if (baseExpression is KtDotQualifiedExpression) {
                    val receiverExpression = baseExpression.receiverExpression
                    if (receiverExpression is KtConstantExpression &&
                        receiverExpression.node.elementType in Holder.numberTypes
                    ) {
                        holder.registerProblem(
                            expression,
                            KotlinBundle.message("wrap.unary.operator.quickfix.text"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            WrapUnaryOperatorQuickfix()
                        )
                    }
                }
            }
        }
    }

    private fun IElementType.isUnaryMinusOrPlus() = this == KtTokens.MINUS || this == KtTokens.PLUS

    private class WrapUnaryOperatorQuickfix : PsiUpdateModCommandQuickFix() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("wrap.unary.operator.quickfix.text")

        override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
            val expression = element as? KtPrefixExpression ?: return
            val dotQualifiedExpression = expression.baseExpression as? KtDotQualifiedExpression ?: return
            val factory = KtPsiFactory(project)
            val newReceiver = factory.createExpressionByPattern(
                "($0$1)",
                expression.operationReference.text,
                dotQualifiedExpression.getLeftMostReceiverExpression()
            )
            val newExpression = dotQualifiedExpression.replaceFirstReceiver(factory, newReceiver)
            expression.replace(newExpression)
        }
    }
}