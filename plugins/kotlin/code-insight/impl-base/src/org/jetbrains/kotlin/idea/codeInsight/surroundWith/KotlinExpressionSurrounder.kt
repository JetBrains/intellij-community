// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.lang.surroundWith.PsiUpdateModCommandSurrounder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

abstract class KotlinExpressionSurrounder : PsiUpdateModCommandSurrounder() {
    final override fun isApplicable(elements: Array<PsiElement>): Boolean {
        if (elements.size != 1 || elements[0] !is KtExpression) {
            return false
        }
        val expression = elements[0] as KtExpression
        return if (expression is KtCallExpression && expression.getParent() is KtQualifiedExpression) {
            false
        } else isApplicable(expression)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    protected open fun isApplicable(expression: KtExpression): Boolean {
        allowAnalysisOnEdt {
            return analyze(expression) {
                val type = expression.expressionType
                if (type == null || type is KaErrorType || type.isUnitType && isApplicableToStatements) {
                    false
                } else {
                    isApplicableToStatements || expression.isUsedAsExpression
                }
            }
        }
    }

    protected open val isApplicableToStatements: Boolean
        get() = true

    final override fun surroundElements(
        context: ActionContext,
        elementsInCopy: Array<out PsiElement>,
        updater: ModPsiUpdater
    ) {
        assert(elementsInCopy.size == 1) { "KotlinExpressionSurrounder should be applicable only for 1 expression: " + elementsInCopy.size }
        val element = elementsInCopy[0]
        assert(element is KtExpression) { "KotlinExpressionSurrounder should be applicable only for expression" }
        surroundExpression(context, element as KtExpression, updater)
    }

    protected abstract fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater)
}
