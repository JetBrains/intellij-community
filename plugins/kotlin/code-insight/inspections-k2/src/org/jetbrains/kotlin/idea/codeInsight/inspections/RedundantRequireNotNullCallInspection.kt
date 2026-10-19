// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.session.analyzeCopy
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isFunctionType
import org.jetbrains.kotlin.analysis.api.types.isNullable
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.RedundantRequireNotNullCallInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isSideEffectFree
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.lambdaExpressionRecursiveVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance

internal class RedundantRequireNotNullCallInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Context>() {
    data class Context(
        val argumentReplacement: KtExpression?,
        val functionName: String,
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

    context(session: KaSession)
    override fun prepareContext(element: KtCallExpression): Context? {
        val call = validateFunctionCall(element) ?: return null
        val functionName = getFunctionName(element) ?: return null
        val argument = extractArgument(call) ?: return null
        val argumentType = argument.expressionType ?: return null
        if (argumentType.isNullable) return null

        val replacement = prepareReplacement(argument)

        return Context(
            replacement.takeIf {
                element.isUsedAsExpression || !argument.isSideEffectFree()
            },
            functionName,
        )
    }

    context(_: KaSession)
    private fun prepareReplacement(argument: KtExpression): KtExpression {
        val fileCopy = argument.containingKtFile.copied()
        val result = PsiTreeUtil.findSameElementInCopy(argument, fileCopy)

        analyzeCopy(result, KaDanglingFileResolutionMode.PREFER_SELF) {
            if (result is KtLambdaExpression) {
                getExplicitLambdaSignature(result)?.let { specifyExplicitLambdaSignature(result, it) }
            }
        }

        return result
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
        val target = element.getQualifiedExpressionForSelector() ?: element
        val replacement = elementContext.argumentReplacement
        if (replacement != null) {
            target.replace(replacement)
        } else {
            target.delete()
        }
    }
}

context(_: KaSession)
private fun extractArgument(call: KaFunctionCall<*>): KtExpression? {
    val parameter = call.valueArgumentMapping.asSequence().find { it.value.name == PARAMETER_NAME } ?: return null
    return parameter.key
}

private fun getFunctionName(element: KtCallExpression): String? =
    element.calleeExpression?.text

context(session: KaSession)
private fun validateFunctionCall(element: KtCallExpression): KaFunctionCall<*>? {
    val call = element.resolveSuccessfulCall() ?: return null
    val symbol = call.symbol
    val callableFqName = symbol.callableId?.asSingleFqName() ?: return null
    if (callableFqName != REQUIRE_NOT_NULL_FQ_NAME && callableFqName != CHECK_NOT_NULL_FQ_NAME) {
        return null
    }

    return call
}

private const val REQUIRE_NOT_NULL_FUNCTION_NAME = "requireNotNull"
private const val CHECK_NOT_NULL_FUNCTION_NAME = "checkNotNull"
private val PARAMETER_NAME = Name.identifier("value")

private val REQUIRE_NOT_NULL_FQ_NAME: FqName
    get() = FqName("kotlin.$REQUIRE_NOT_NULL_FUNCTION_NAME")

private val CHECK_NOT_NULL_FQ_NAME: FqName
    get() = FqName("kotlin.$CHECK_NOT_NULL_FUNCTION_NAME")
