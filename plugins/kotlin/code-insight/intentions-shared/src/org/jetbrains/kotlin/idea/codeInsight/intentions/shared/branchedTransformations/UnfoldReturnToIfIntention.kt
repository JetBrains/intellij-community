// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.inspections.createReturnExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

internal class UnfoldReturnToIfIntention : KotlinApplicableModCommandAction.Simple<KtReturnExpression>(KtReturnExpression::class) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val ifExpression = element.returnedExpression as KtIfExpression
        val thenExpr = ifExpression.then?.lastBlockStatementOrThis() ?: return
        val elseExpr = ifExpression.`else`?.lastBlockStatementOrThis()

        val newIfExpression = ifExpression.copied()
        val newThenExpr = newIfExpression.then?.lastBlockStatementOrThis() ?: return
        val newElseExpr = newIfExpression.`else`?.lastBlockStatementOrThis()

        val psiFactory = KtPsiFactory(element.project)
        val labelName = element.getLabelName()
        newThenExpr.replace(createReturnExpression(thenExpr, labelName, psiFactory))
        if (elseExpr != null) {
            newElseExpr?.replace(createReturnExpression(elseExpr, labelName, psiFactory))
        }
        element.replace(newIfExpression)
    }

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> {
        val ifExpression = element.returnedExpression as? KtIfExpression ?: return emptyList()
        if (ifExpression.then == null) return emptyList()

        val returnKeywordRange = element.returnKeyword.textRangeIn(element)
        val ifKeywordRange = ifExpression.ifKeyword.textRangeIn(element)

        return listOf(
            returnKeywordRange.union(ifKeywordRange)
        )
    }

    override fun isApplicableByPsi(element: KtReturnExpression): Boolean = element.returnedExpression is KtIfExpression

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.return.with.if.expression")

    override fun getPresentation(context: ActionContext, element: KtReturnExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
}