// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ReplaceGetOrSetInspectionUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceGetOrSetInspection :
    AbstractKotlinApplicatorBasedInspection<KtDotQualifiedExpression, ReplaceGetOrSetInspection.ReplaceGetOrSetInput>(
        KtDotQualifiedExpression::class
    ) {
    data class ReplaceGetOrSetInput(val calleeName: Name) : KotlinApplicatorInput

    override fun getApplicabilityRange() = applicabilityRange { dotQualifiedExpression: KtDotQualifiedExpression ->
        dotQualifiedExpression.getPossiblyQualifiedCallExpression()?.calleeExpression?.textRangeIn(dotQualifiedExpression)
    }

    override fun getInputProvider() = inputProvider { dotQualifiedExpression: KtDotQualifiedExpression ->
        val call = (dotQualifiedExpression.resolveCall() as? KtSuccessCallInfo)?.call as? KtSimpleFunctionCall ?: return@inputProvider null
        val functionSymbol = call.partiallyAppliedSymbol.symbol
        if (functionSymbol !is KtFunctionSymbol || !functionSymbol.isOperator) return@inputProvider null

        val receiverExpression = dotQualifiedExpression.receiverExpression
        if (receiverExpression is KtSuperExpression || receiverExpression.getKtType()?.isUnit != false) return@inputProvider null

        if (functionSymbol.name == OperatorNameConventions.SET &&
            dotQualifiedExpression.getPossiblyQualifiedCallExpression()?.getKtType()?.isUnit != true &&
            dotQualifiedExpression.isExpressionResultValueUsed()
        ) return@inputProvider null
        return@inputProvider ReplaceGetOrSetInput(functionSymbol.name)
    }

    override fun getApplicator() = applicator<KtDotQualifiedExpression, ReplaceGetOrSetInput> {
        familyName(KotlinBundle.lazyMessage(("inspection.replace.get.or.set.display.name")))
        actionName { _, input ->
            KotlinBundle.message("replace.0.call.with.indexing.operator", input.calleeName)
        }
        isApplicableByPsi { expression -> ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(expression) }
        applyTo { expression, input, _, editor ->
            ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
                expression,
                input.calleeName == OperatorNameConventions.SET,
                editor
            )
        }
    }

    /**
     * A method that returns whether the value of the KtExpression is used by other expression or not.
     *
     * TODO: This is a stub for the `fun KtElement.isUsedAsExpression(context: BindingContext): Boolean` implementation.
     *       It should be replaced with when https://youtrack.jetbrains.com/issue/KT-50250 is implemented.
     */
    private fun KtExpression.isExpressionResultValueUsed(): Boolean {
        return when (parent) {
            is KtOperationExpression,
            is KtCallExpression,
            is KtReferenceExpression,
            is KtVariableLikeSymbol,
            is KtReturnExpression,
            is KtThrowExpression -> {
                true
            }
            is KtIfExpression,
            is KtWhenExpression,
            is KtLoopExpression,
            is KtDotQualifiedExpression -> {
                (parent as KtExpression).isExpressionResultValueUsed()
            }
            else -> false
        }
    }
}