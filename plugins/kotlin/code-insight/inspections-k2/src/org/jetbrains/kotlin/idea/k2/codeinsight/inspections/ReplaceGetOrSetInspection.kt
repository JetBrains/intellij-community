// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ReplaceGetOrSetInspectionUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class ReplaceGetOrSetInspection :
    AbstractKotlinApplicableInspectionWithContext<KtDotQualifiedExpression, ReplaceGetOrSetInspection.Context>(
        KtDotQualifiedExpression::class
    ) {

    class Context(val calleeName: Name)

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Context): String =
        KotlinBundle.message("explicit.0.call", context.calleeName)

    override fun getActionFamilyName(): String = KotlinBundle.message("replace.get.or.set.call.with.indexing.operator")

    override fun getActionName(element: KtDotQualifiedExpression, context: Context): String =
        KotlinBundle.message("replace.0.call.with.indexing.operator", context.calleeName)

    override fun getApplicabilityRange() = applicabilityRange { dotQualifiedExpression: KtDotQualifiedExpression ->
        dotQualifiedExpression.getPossiblyQualifiedCallExpression()?.calleeExpression?.textRangeIn(dotQualifiedExpression)
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean =
        ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDotQualifiedExpression): Context? {
        // `resolveCall()` is needed to filter out `set` functions with varargs or default values. See the `setWithVararg.kt` test.
        val call = element.resolveCall()?.successfulCallOrNull<KtSimpleFunctionCall>() ?: return null
        val functionSymbol = call.symbol
        if (functionSymbol !is KtFunctionSymbol || !functionSymbol.isOperator) {
            return null
        }

        val receiverExpression = element.receiverExpression
        if (receiverExpression is KtSuperExpression || receiverExpression.getKtType()?.isUnit != false) return null

        if (functionSymbol.name == OperatorNameConventions.SET &&
            element.getPossiblyQualifiedCallExpression()?.getKtType()?.isUnit != true &&
            element.isExpressionResultValueUsed()
        ) return null
        return Context(functionSymbol.name)
    }

    override fun apply(element: KtDotQualifiedExpression, context: Context, project: Project, editor: Editor?) {
        ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
            element,
            isSet = context.calleeName == OperatorNameConventions.SET,
            editor
        )
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