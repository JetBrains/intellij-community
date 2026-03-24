// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.idea.base.analysis.api.utils.findSamSymbolOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.types.Variance

internal class SamConversionToAnonymousObjectIntention :
    KotlinApplicableModCommandAction<KtCallExpression, SamConversionToAnonymousObjectIntention.Context>(KtCallExpression::class) {

    data class Context(
        val interfaceName: String,
        val typeArgumentsText: String,
        val functionText: String,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.anonymous.object")

    override fun getPresentation(
        context: ActionContext,
        element: KtCallExpression,
    ): Presentation = Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        getLambdaExpression(element) != null

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val lambda = getLambdaExpression(element) ?: return null
        val functionLiteral = lambda.functionLiteral

        val callType = element.expressionType as? KaClassType ?: return null
        if (!callType.isFunctionalInterface) return null

        val classSymbol = callType.expandedSymbol as? KaNamedClassSymbol ?: return null
        val samMethod = classSymbol.findSamSymbolOrNull() ?: return null

        val lambdaSymbol = functionLiteral.symbol
        val samParameters = samMethod.valueParameters
        if (samParameters.size != lambdaSymbol.valueParameters.size) return null

        if (lambdaSymbol.valueParameters.any { it.returnType is KaErrorType }) return null

        val samName = samMethod.name.asString()
        val hasRecursiveCall = functionLiteral.anyDescendantOfType<KtCallExpression> { call ->
            if (call.calleeExpression?.text != samName) return@anyDescendantOfType false
            val callArguments = call.valueArguments
            if (callArguments.size != samParameters.size) return@anyDescendantOfType false

            callArguments.zip(samParameters).all { (arg, param) ->
                val argType = arg.getArgumentExpression()?.expressionType ?: return@all false
                argType.isSubtypeOf(param.returnType)
            }
        }
        if (hasRecursiveCall) return null

        val callee = element.calleeExpression
        if (callee != null && callee.isAliasedWithVariance()) return null

        val interfaceName = callType.getInterfaceName()
        val typeArgumentsText = computeTypeArguments(element, callType, classSymbol)
        val samParameterNames = samMethod.valueParameters.map { it.name.asString() }

        val functionText = LambdaToAnonymousFunctionUtil.prepareFunctionText(
            lambda = lambda,
            functionName = samName,
            parameterNames = samParameterNames,
            forceNonNullReturnType = true,
        ) ?: return null

        return Context(
            interfaceName,
            typeArgumentsText,
            functionText,
        )
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(element.project)

        val stubFunction = psiFactory.createFunction(elementContext.functionText)
        stubFunction.addModifier(KtTokens.OVERRIDE_KEYWORD)

        val objectLiteral = psiFactory.createExpression(
            "${KtTokens.OBJECT_KEYWORD} : ${elementContext.interfaceName}${elementContext.typeArgumentsText} { ${stubFunction.text} }"
        )

        val parentOfCall = element.getQualifiedExpressionForSelector() ?: element
        val replaced = parentOfCall.replaced(objectLiteral)

        shortenReferences(replaced)
    }
}

private fun getLambdaExpression(element: KtCallExpression): KtLambdaExpression? =
    element.lambdaArguments.firstOrNull()?.getLambdaExpression()
        ?: element.valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression

context(_: KaSession)
private fun KtExpression.isAliasedWithVariance(): Boolean {
    val resolvedSymbol = mainReference?.resolveToSymbol() as? KaSamConstructorSymbol ?: return false
    return resolvedSymbol.returnType
        .takeIf { it.abbreviation?.symbol is KaTypeAliasSymbol }
        ?.hasVariance()
        ?: false
}

private fun KaType.hasVariance(): Boolean =
    (this as? KaClassType)
        ?.typeArguments
        ?.filterIsInstance<KaTypeArgumentWithVariance>()
        ?.any { it.variance != Variance.INVARIANT || it.type.hasVariance() }
        ?: false

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
