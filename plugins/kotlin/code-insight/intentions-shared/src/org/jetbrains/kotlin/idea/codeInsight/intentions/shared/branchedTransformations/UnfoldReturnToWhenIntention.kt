// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.inspections.createReturnOrEmptyText
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

internal class UnfoldReturnToWhenIntention : KotlinApplicableModCommandAction<KtReturnExpression, List<String>>(KtReturnExpression::class) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: List<String>,
        updater: ModPsiUpdater
    ) {
        val whenExpression = element.returnedExpression as? KtWhenExpression ?: return
        val newWhenExpression = whenExpression.copied()

        val ktPsiFactory = KtPsiFactory(element.project)
        newWhenExpression.entries.map { it.expression!!.lastBlockStatementOrThis() }.zip(elementContext)
            .forEach { (expr, newExpressionText) ->
                expr.replace(ktPsiFactory.createExpressionByPattern("$newExpressionText$0", expr))
            }

        element.replace(newWhenExpression)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.return.with.when.expression")

    override fun getPresentation(context: ActionContext, element: KtReturnExpression): Presentation? =
        if (isElementApplicable(element, context)) {
            Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
        } else {
            null
        }

    override fun KaSession.prepareContext(element: KtReturnExpression): List<String>? {
        val whenExpression = element.returnedExpression as? KtWhenExpression ?: return null
        val labelName = element.getLabelName()

        return whenExpression.entries.map {
            val expr = it.expression!!.lastBlockStatementOrThis()
            createReturnOrEmptyText(expr, labelName)
        }
    }

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> {
        val whenExpr = element.returnedExpression as? KtWhenExpression ?: return listOf()
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(whenExpr)) return listOf()
        if (whenExpr.entries.any { it.expression == null }) return listOf()

        val returnKeywordRange = element.returnKeyword.textRangeIn(element)
        val whenKeywordRange = whenExpr.whenKeyword.textRangeIn(element)

        return listOf(returnKeywordRange.union(whenKeywordRange))
    }
}
