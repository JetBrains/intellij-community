// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceCallWithUnaryOperatorIntention : KotlinApplicableModCommandAction<KtDotQualifiedExpression, Unit>(KtDotQualifiedExpression::class) {

    override fun getPresentation(
        context: ActionContext,
        element: KtDotQualifiedExpression
    ): Presentation? {
        val operation = getOperationToken(element) ?: return null
        return Presentation.of(KotlinBundle.message("replace.with.0.operator", operation))
            .withPriority(PriorityAction.Priority.HIGH)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.call.with.unary.operator")

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> {
        val selectorExpression = element.selectorExpression as? KtCallExpression ?: return emptyList()
        val calleeExpression = selectorExpression.calleeExpression ?: return emptyList()
        return listOf(calleeExpression.textRange.shiftLeft(element.startOffset))
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val selectorExpression = element.selectorExpression as? KtCallExpression ?: return false
        if (getOperationToken(element) == null) return false

        if (selectorExpression.typeArgumentList != null) return false
        if (selectorExpression.valueArguments.isNotEmpty()) return false
        if (element.receiverExpression is KtSuperExpression) return false

        return true
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        // Check if the receiver expression has a value
        return (element.receiverExpression.expressionType != null).asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val operation = getOperationToken(element) ?: return
        val receiver = element.receiverExpression
        element.replace(KtPsiFactory(element.project).createExpressionByPattern("$0$1", operation, receiver))
    }

    private fun getOperationToken(element: KtDotQualifiedExpression): String? {
        val functionName = (element.callExpression?.calleeExpression as? KtNameReferenceExpression)?.text ?: return null
        if (functionName == OperatorNameConventions.INC.identifier || functionName == OperatorNameConventions.DEC.identifier) return null
        return OperatorNameConventions.TOKENS_BY_OPERATOR_NAME.entries.find { (name, _) ->
            name.identifier == functionName
        }?.value
    }
}
