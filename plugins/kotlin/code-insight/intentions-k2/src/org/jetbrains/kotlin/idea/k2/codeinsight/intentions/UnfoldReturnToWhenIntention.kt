// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

class UnfoldReturnToWhenIntention : LowPriorityAction,
                                    KotlinApplicableModCommandAction<KtReturnExpression, List<String>>(KtReturnExpression::class) {
    override fun invoke(
        actionContext: ActionContext, element: KtReturnExpression, elementContext: List<String>, updater: ModPsiUpdater
    ) {
        val whenExpression = element.returnedExpression as? KtWhenExpression ?: return
        val newWhenExpression = whenExpression.copied()

        newWhenExpression.entries.map { it.expression!!.lastBlockStatementOrThis() }.zip(elementContext)
            .forEach { (expr, newExpressionText) ->
                expr.replace(KtPsiFactory(element.project).createExpressionByPattern("$newExpressionText$0", expr))
            }

        element.replace(newWhenExpression)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.return.with.when.expression")

    context(KaSession@KaSession)
    override fun prepareContext(element: KtReturnExpression): List<String>? {
        val whenExpression = element.returnedExpression as? KtWhenExpression ?: return null
        val labelName = element.getLabelName()

        return whenExpression.entries.map {
            val expr = it.expression!!.lastBlockStatementOrThis()
            Holder.createReturnExpressionText(expr, labelName)
        }
    }

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> {
        val whenExpr = element.returnedExpression as? KtWhenExpression ?: return listOf()
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(whenExpr)) return return listOf()
        if (whenExpr.entries.any { it.expression == null }) return return listOf()

        return ApplicabilityRange.multiple(element) {
            listOf(it.returnKeyword, whenExpr.whenKeyword)
        }
    }
}

object Holder {
    context(KaSession@KaSession)
    fun createReturnExpressionText(
        expr: KtExpression,
        labelName: String?,
    ): String {
        val label = labelName?.let { "@$it" } ?: ""

        return when (expr) {
            is KtBreakExpression, is KtContinueExpression, is KtReturnExpression, is KtThrowExpression -> ""
            else -> {
                val isNothingType = analyze(expr) {
                    expr.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.signature?.returnType?.isNothingType
                }

                if (isNothingType == true) "" else "return$label "
            }
        }
    }
}