// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.findSamSymbolOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.SamConversionToAnonymousObjectContext
import org.jetbrains.kotlin.idea.k2.codeinsight.applySamConversionToAnonymousObject
import org.jetbrains.kotlin.idea.k2.codeinsight.getLambdaExpressionForSamConversion
import org.jetbrains.kotlin.idea.k2.codeinsight.hasRecursiveSamCall
import org.jetbrains.kotlin.idea.k2.codeinsight.isSamConversionAliasedWithVariance
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.types.Variance

internal class SamConversionToAnonymousObjectIntention :
    KotlinApplicableModCommandAction<KtCallExpression, SamConversionToAnonymousObjectContext>(KtCallExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.anonymous.object")

    override fun getActionPresentation(
        context: ActionContext,
        element: KtCallExpression,
    ): Presentation = Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.getLambdaExpressionForSamConversion() != null

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun KaSession.prepareContext(element: KtCallExpression): SamConversionToAnonymousObjectContext? {
        val lambda = element.getLambdaExpressionForSamConversion() ?: return null
        val functionLiteral = lambda.functionLiteral

        val callType = element.expressionType as? KaClassType ?: return null
        if (!callType.isFunctionalInterface) return null

        val classSymbol = callType.expandedSymbol as? KaNamedClassSymbol ?: return null
        val samMethod = classSymbol.findSamSymbolOrNull() ?: return null

        val lambdaSymbol = functionLiteral.symbol
        val samParameters = samMethod.valueParameters
        val lambdaParameters = lambdaSymbol.valueParameters
        if (samParameters.size != lambdaParameters.size) return null

        if (lambdaParameters.any { it.returnType is KaErrorType }) return null

        val samName = samMethod.name.asString()
        if (functionLiteral.hasRecursiveSamCall(samName, lambdaParameters)) return null

        val callee = element.calleeExpression
        if (callee != null && callee.isSamConversionAliasedWithVariance()) return null

        val interfaceName = callType.getInterfaceName()
        val typeArgumentsText = computeTypeArguments(element, callType, classSymbol)
        val samParameterNames = samMethod.valueParameters.map { it.name.asString() }

        val functionText = LambdaToAnonymousFunctionUtil.prepareFunctionText(
            lambda = lambda,
            functionName = samName,
            parameterNames = samParameterNames,
            forceNonNullReturnType = true,
        ) ?: return null

        return SamConversionToAnonymousObjectContext(
            interfaceName,
            typeArgumentsText,
            functionText,
        )
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: SamConversionToAnonymousObjectContext,
        updater: ModPsiUpdater,
    ) {
        applySamConversionToAnonymousObject(element, elementContext)
    }
}

private fun KaClassType.getInterfaceName(): String =
    (abbreviation ?: this).classId.asFqNameString()

@OptIn(KaExperimentalApi::class)
private fun KaSession.computeTypeArguments(
    element: KtCallExpression,
    callType: KaClassType,
    classSymbol: KaNamedClassSymbol,
): String {
    val explicitTypeArguments = element.typeArgumentList?.arguments
    if (!explicitTypeArguments.isNullOrEmpty()) {
        val renderedTypes = explicitTypeArguments.mapNotNull { typeArg ->
            typeArg.typeReference?.type?.render(position = Variance.IN_VARIANCE)
        }
        return if (renderedTypes.isNotEmpty()) {
            renderedTypes.joinToString(prefix = "<", postfix = ">", separator = ", ")
        } else ""
    }

    val typeArguments = (callType.abbreviation ?: callType).typeArguments
    val declaredTypeParameters = classSymbol.typeParameters

    if (typeArguments.isEmpty() || declaredTypeParameters.isEmpty()) return ""

    val renderedTypes = typeArguments.take(declaredTypeParameters.size).mapNotNull { arg ->
        arg.type?.takeIf { it !is KaErrorType }?.render(position = Variance.IN_VARIANCE)
    }

    return if (renderedTypes.isNotEmpty()) {
        renderedTypes.joinToString(prefix = "<", postfix = ">", separator = ", ")
    } else ""
}
