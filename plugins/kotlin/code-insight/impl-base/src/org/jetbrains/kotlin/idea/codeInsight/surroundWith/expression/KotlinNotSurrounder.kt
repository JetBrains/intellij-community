// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinNotSurrounder : KotlinExpressionSurrounder() {
    override fun getTemplateDescription(): String {
        return CodeInsightBundle.message("surround.with.not.template")
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    public override fun isApplicable(expression: KtExpression): Boolean {
        if (!super.isApplicable(expression)) return false
        allowAnalysisOnEdt {
            return analyze(expression) {
                val ktType = expression.expressionType
                ktType != null && ktType.isBooleanType
            }
        }
    }

    override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(expression.project)
        val prefixExpr = factory.createExpression("!(a)") as KtPrefixExpression
        val parenthesizedExpression = prefixExpr.baseExpression as KtParenthesizedExpression? ?: error(
            "KtParenthesizedExpression should exists for " + prefixExpr.text + " expression"
        )
        val expressionWithoutParentheses = parenthesizedExpression.expression ?: error(
            "KtExpression should exists for " + parenthesizedExpression.text + " expression"
        )
        expressionWithoutParentheses.replace(expression)
        val expr = expression.replace(prefixExpr) as KtExpression
        updater.select(TextRange.from(expr.textRange.endOffset, 0))
    }
}