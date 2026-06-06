// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.findSamSymbolOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.SamConversionToAnonymousObjectContext
import org.jetbrains.kotlin.idea.k2.codeinsight.applySamConversionToAnonymousObject
import org.jetbrains.kotlin.idea.k2.codeinsight.getLambdaExpressionForSamConversion
import org.jetbrains.kotlin.idea.k2.codeinsight.hasRecursiveSamCall
import org.jetbrains.kotlin.idea.k2.codeinsight.isSamConversionAliasedWithVariance
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.Variance

internal object ConvertToAnonymousObjectFixFactories {
    val fixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InterfaceAsFunction ->
        listOfNotNull(createQuickFix(diagnostic))
    }

    context(_: KaSession)
    private fun createQuickFix(diagnostic: KaFirDiagnostic.InterfaceAsFunction): ModCommandAction? {
        val call = diagnostic.psi.parent as? KtCallExpression ?: return null
        val lambda = call.getLambdaExpressionForSamConversion() ?: return null
        val functionLiteral = lambda.functionLiteral

        val classSymbol = getFunctionalInterfaceSymbol(diagnostic, call) ?: return null
        val samMethod = classSymbol.findSamSymbolOrNull() ?: return null

        val lambdaParameters = functionLiteral.symbol.valueParameters
        if (lambdaParameters.any { it.returnType is KaErrorType }) return null

        val samName = samMethod.name.asString()
        if (functionLiteral.hasRecursiveSamCall(samName, lambdaParameters)) return null

        val callee = call.calleeExpression
        if (callee != null && callee.isSamConversionAliasedWithVariance()) return null

        val interfaceName = computeInterfaceName(call, classSymbol) ?: return null
        val typeArguments = computeTypeArguments(call, classSymbol, samMethod, functionLiteral.symbol) ?: return null
        val functionText = prepareFunctionText(lambda, classSymbol, samMethod, typeArguments.types) ?: return null

        return ConvertToAnonymousObjectFix(
            call,
            SamConversionToAnonymousObjectContext(
                interfaceName = interfaceName,
                typeArgumentsText = typeArguments.text,
                functionText = functionText,
            ),
        )
    }

    private class ConvertToAnonymousObjectFix(
        element: KtCallExpression,
        elementContext: SamConversionToAnonymousObjectContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtCallExpression, SamConversionToAnonymousObjectContext>(element, elementContext) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("convert.to.anonymous.object")

        override fun invoke(
            actionContext: ActionContext,
            element: KtCallExpression,
            elementContext: SamConversionToAnonymousObjectContext,
            updater: ModPsiUpdater,
        ): Unit = applySamConversionToAnonymousObject(element, elementContext)
    }
}

private data class TypeArguments(
    val text: String,
    val types: List<KaType>,
)

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun prepareFunctionText(
    lambda: KtLambdaExpression,
    classSymbol: KaNamedClassSymbol,
    samMethod: KaNamedFunctionSymbol,
    actualTypeArguments: List<KaType>,
): String? {
    val psiFactory = KtPsiFactory.contextual(lambda)
    val preparedFunctionText = LambdaToAnonymousFunctionUtil.prepareFunctionText(
        lambda = lambda,
        functionName = samMethod.name.asString(),
        parameterNames = samMethod.valueParameters.map { it.name.asString() },
        forceNonNullReturnType = true,
    ) ?: return null
    val preparedFunction = psiFactory.createFunction(preparedFunctionText)
    val bodyText = preparedFunction.bodyBlockExpression?.text ?: return null

    val substitutor = buildSubstitutor {
        classSymbol.typeParameters.zip(actualTypeArguments).forEach { (typeParameter, actualType) ->
            substitution(typeParameter, actualType)
        }
    }
    val useSamParameterNames = lambda.functionLiteral.valueParameterList == null
    val parameterTexts = samMethod.valueParameters.mapIndexed { index, parameter ->
        val parameterName = when {
            useSamParameterNames -> parameter.renderedName(index)
            else -> preparedFunction.valueParameters.getOrNull(index)?.name?.quoteIfNeeded() ?: parameter.renderedName(index)
        }
        val parameterType = substitutor.substitute(parameter.returnType)
        "$parameterName: ${parameterType.render(position = Variance.IN_VARIANCE)}"
    }

    val returnTypeText = samMethod.returnType
        .takeIf { !it.isUnitType && it !is KaErrorType }
        ?.let(substitutor::substitute)
        ?.let { returnType ->
            val adjustedType = if (preparedFunction.typeReference?.text?.endsWith("?") == false) {
                returnType.withNullability(false)
            } else {
                returnType
            }
            adjustedType.render(position = Variance.OUT_VARIANCE)
        }

    return buildString {
        append("fun ")
        append(samMethod.name.asString())
        append("(")
        append(parameterTexts.joinToString(separator = ", "))
        append(")")
        if (returnTypeText != null) {
            append(": ")
            append(returnTypeText)
        }
        append(" ")
        append(bodyText)
    }
}

context(_: KaSession)
private fun getFunctionalInterfaceSymbol(
    diagnostic: KaFirDiagnostic.InterfaceAsFunction,
    call: KtCallExpression,
): KaNamedClassSymbol? {
    return (diagnostic.classSymbol as? KaNamedClassSymbol)
        ?: ((call.calleeExpression?.mainReference?.resolveToSymbol() as? KaTypeAliasSymbol)?.expandedType?.expandedSymbol as? KaNamedClassSymbol)
}

private fun computeInterfaceName(call: KtCallExpression, classSymbol: KaNamedClassSymbol): String? {
    return if (call.getQualifiedExpressionForSelector() != null) {
        classSymbol.classId?.asFqNameString()
    } else {
        call.calleeExpression?.text ?: classSymbol.classId?.asFqNameString()
    }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun computeTypeArguments(
    element: KtCallExpression,
    classSymbol: KaNamedClassSymbol,
    samMethod: KaFunctionSymbol,
    lambdaSymbol: KaFunctionSymbol,
): TypeArguments? {
    val explicitTypeArguments = element.typeArgumentList?.arguments
    if (!explicitTypeArguments.isNullOrEmpty()) {
        val renderedTypes = mutableListOf<String>()
        val actualTypes = mutableListOf<KaType>()
        explicitTypeArguments.forEach { typeArgument ->
            val typeReference = typeArgument.typeReference ?: return null
            renderedTypes += typeReference.text
            actualTypes += typeReference.type
        }
        return TypeArguments(
            text = renderedTypes.joinToString(prefix = "<", postfix = ">", separator = ", "),
            types = actualTypes,
        )
    }

    val declaredTypeParameters = classSymbol.typeParameters
    if (declaredTypeParameters.isEmpty()) return TypeArguments("", emptyList())

    val actualTypes = declaredTypeParameters.mapNotNull { typeParameter ->
        inferTypeArgument(
            typeParameter,
            samMethod.valueParameters.map { it.returnType },
            lambdaSymbol.valueParameters.map { it.returnType })
            ?: inferTypeArgument(typeParameter, samMethod.returnType, lambdaSymbol.returnType)
    }
    val renderedTypes = actualTypes.mapNotNull { type ->
        type.takeIf { it !is KaErrorType }?.render(position = Variance.IN_VARIANCE)
    }
    return if (actualTypes.size == declaredTypeParameters.size && renderedTypes.size == declaredTypeParameters.size) {
        TypeArguments(
            text = renderedTypes.joinToString(prefix = "<", postfix = ">", separator = ", "),
            types = actualTypes,
        )
    } else {
        null
    }
}

private fun KaValueParameterSymbol.renderedName(index: Int): String {
    val nameToUse = name.takeUnless { it.isSpecial }?.asString() ?: "p${index + 1}"
    return nameToUse.quoteIfNeeded()
}

private fun inferTypeArgument(typeParameter: KaTypeParameterSymbol, expectedTypes: List<KaType>, actualTypes: List<KaType>): KaType? {
    if (expectedTypes.size != actualTypes.size) return null
    return expectedTypes.zip(actualTypes).firstNotNullOfOrNull { (expectedType, actualType) ->
        inferTypeArgument(typeParameter, expectedType, actualType)
    }
}

private fun inferTypeArgument(typeParameter: KaTypeParameterSymbol, expectedType: KaType?, actualType: KaType?): KaType? {
    if (expectedType == null || actualType == null) return null
    return when (expectedType) {
        is KaTypeParameterType -> actualType.takeIf { expectedType.symbol == typeParameter }
        is KaClassType -> {
            val actualClassType = actualType as? KaClassType ?: return null
            val expectedArguments = expectedType.typeArguments.filterIsInstance<KaTypeArgumentWithVariance>().map { it.type }
            val actualArguments = actualClassType.typeArguments.filterIsInstance<KaTypeArgumentWithVariance>().map { it.type }
            inferTypeArgument(typeParameter, expectedArguments, actualArguments)
        }

        else -> null
    }
}
