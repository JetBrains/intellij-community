// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

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
import org.jetbrains.kotlin.idea.inspections.createReturnExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

class UnfoldReturnToWhenIntention : KotlinApplicableModCommandAction<KtReturnExpression, Unit>(KtReturnExpression::class) {

    override fun getApplicableRanges(returnExpression: KtReturnExpression): List<TextRange> {
        val whenExpr = returnExpression.returnedExpression as? KtWhenExpression ?: return listOf()
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(whenExpr)) return return listOf()
        if (whenExpr.entries.any { it.expression == null }) return return listOf()

        val returnKeywordRange = returnExpression.returnKeyword.textRangeIn(returnExpression)
        val whenKeywordRange = whenExpr.whenKeyword.textRangeIn(returnExpression)

        return listOf(returnKeywordRange.union(whenKeywordRange))
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)

        val whenExpression = element.returnedExpression as KtWhenExpression
        val newWhenExpression = whenExpression.copied()
        val labelName = element.getLabelName()
        whenExpression.entries.zip(newWhenExpression.entries).forEach { (entry, newEntry) ->
            val expr = entry.expression!!.lastBlockStatementOrThis()
            val newExpr = newEntry.expression!!.lastBlockStatementOrThis()
            newExpr.replace(createReturnExpression(expr, labelName, psiFactory))
        }

        element.replace(newWhenExpression)
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("replace.return.with.when.expression")
    }

    override fun getPresentation(context: ActionContext, element: KtReturnExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    context(KaSession)
    override fun prepareContext(element: KtReturnExpression) {
    }
}
