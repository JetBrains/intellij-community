// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RedundantRequireNotNullCallInspection.Context
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

internal class RedundantRequireNotNullCallInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Context>() {
    data class Context(
        val functionName: String,
        val isUsedAsExpression: Boolean,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val functionName = getFunctionName(element) ?: return false
        return functionName == REQUIRE_NOT_NULL_FUNCTION_NAME ||
                functionName == CHECK_NOT_NULL_FUNCTION_NAME
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        if (!validateFunctionCall(element)) return null
        val functionName = getFunctionName(element) ?: return null
        val argument = extractArgument(element) ?: return null
        val argumentType = argument.expressionType ?: return null
        if (argumentType.canBeNull) return null

        return Context(
            functionName,
            element.isUsedAsExpression,
        )
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun getProblemDescription(element: KtCallExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("redundant.0.call", context.functionName)

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = RemoveRedundantNotNullCallFix(context)
}

private class RemoveRedundantNotNullCallFix(
    private val elementContext: Context,
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.require.not.null.call.fix.text", elementContext.functionName)

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater,
    ) {
        val argument = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        val target = element.getQualifiedExpressionForSelector() ?: element
        if (elementContext.isUsedAsExpression) {
            target.replace(argument)
        } else {
            target.delete()
        }
    }
}

private fun extractArgument(element: KtCallExpression): KtReferenceExpression? =
    element.valueArguments.firstOrNull()?.getArgumentExpression()?.referenceExpression()

private fun getFunctionName(element: KtCallExpression): String? =
    element.calleeExpression?.text

private fun KaSession.validateFunctionCall(element: KtCallExpression): Boolean {
    val call = element.resolveToCall() ?: return false
    val callableFqName = call.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName() ?: return false
    return callableFqName == REQUIRE_NOT_NULL_FQ_NAME ||
            callableFqName == CHECK_NOT_NULL_FQ_NAME
}

private const val REQUIRE_NOT_NULL_FUNCTION_NAME = "requireNotNull"
private const val CHECK_NOT_NULL_FUNCTION_NAME = "checkNotNull"

private val REQUIRE_NOT_NULL_FQ_NAME: FqName
    get() = FqName("kotlin.$REQUIRE_NOT_NULL_FUNCTION_NAME")

private val CHECK_NOT_NULL_FQ_NAME: FqName
    get() = FqName("kotlin.$CHECK_NOT_NULL_FUNCTION_NAME")
