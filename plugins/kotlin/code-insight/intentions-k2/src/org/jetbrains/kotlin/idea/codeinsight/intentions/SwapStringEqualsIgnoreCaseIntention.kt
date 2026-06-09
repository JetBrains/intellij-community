// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal class SwapStringEqualsIgnoreCaseIntention :
    KotlinApplicableModCommandAction<KtDotQualifiedExpression, Unit>(KtDotQualifiedExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("flip.equals")

    override fun getActionPresentation(context: ActionContext, element: KtDotQualifiedExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { (it.selectorExpression as? KtCallExpression)?.calleeExpression }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return false
        if (callExpression.calleeExpression?.text != EQUALS_SHORT_NAME) return false
        return callExpression.valueArguments.mapNotNull { it.getArgumentExpression() }.size == 2
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return null
        val resolvedFqName = callExpression.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.symbol
            ?.callableId
            ?.asSingleFqName() ?: return null
        if (resolvedFqName != EQUALS_FQ_NAME) return null
        return Unit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return
        val valueArguments = callExpression.valueArguments
        val receiverExpression = element.receiverExpression
        val newElement = KtPsiFactory(element.project).createExpressionByPattern(
            "$0.equals($1, $2)",
            valueArguments[0].getArgumentExpression()!!,
            receiverExpression,
            valueArguments[1].text
        )
        val replaced = element.replaced(newElement) as? KtDotQualifiedExpression
        (replaced?.selectorExpression as? KtCallExpression)?.calleeExpression?.let {
            updater.moveCaretTo(it)
        }
    }
}

private val EQUALS_FQ_NAME = FqName("kotlin.text.equals")
private const val EQUALS_SHORT_NAME = "equals"
