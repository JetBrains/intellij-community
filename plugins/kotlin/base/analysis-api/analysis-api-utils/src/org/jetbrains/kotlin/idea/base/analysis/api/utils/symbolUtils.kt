// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.findTopmostParentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.getSamConstructorValueArgument
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn
import org.jetbrains.kotlin.utils.keysToMapExceptNulls

fun KaSymbolOrigin.isJavaSourceOrLibrary(): Boolean = this == KaSymbolOrigin.JAVA_SOURCE || this == KaSymbolOrigin.JAVA_LIBRARY

/**
 * Returns the same result as [KaSession.allOverriddenSymbols],
 * but includes the original symbol ([this]) at the beginning of the
 * resulting [Sequence].
 */
context(_: KaSession)
val KaCallableSymbol.allOverriddenSymbolsWithSelf: Sequence<KaCallableSymbol>
    get() {
        val originalSymbol = this

        return sequence {
            yield(originalSymbol)
            yieldAll(originalSymbol.allOverriddenSymbols)
        }
    }

context(_: KaSession)
fun KaNamedClassSymbol.findSamSymbolOrNull(useDeclaredMemberScope: Boolean = true): KaNamedFunctionSymbol? {
    if (classKind != KaClassKind.INTERFACE) return null
    val scope = if (useDeclaredMemberScope) declaredMemberScope else memberScope
    val singleAbstractMember = scope
        .callables
        .filter { it.modality == KaSymbolModality.ABSTRACT }
        .singleOrNull() as? KaNamedFunctionSymbol ?: return null
    return singleAbstractMember.takeIf { it.typeParameters.isEmpty() }
}

/**
 * Creates a pair of copies of the original expression to the o call expression.
 *
 * The original expression could be a variable declaration (when it is available),
 * or a call expression with qualified expression (when it is available).
 */
private fun createAnalyzableExpression(
    originalCall: KtCallExpression,
    callArgumentMap: Map<KtValueArgument, KtCallExpression>
): Pair<KtExpression, KtCallExpression>? {
    // need to copy the entire expression, with fqnames and declaration if it is applicable
    val qualifiedExpression = originalCall.getQualifiedExpressionForSelectorOrThis()
    val qualifiedExpressionParent = qualifiedExpression.parent as? KtDeclaration ?: qualifiedExpression
    val copied = qualifiedExpressionParent.copied()
    val psiFactory = KtPsiFactory(originalCall.project)

    val offset = originalCall.getStartOffsetIn(qualifiedExpressionParent)

    val newCall = copied.findElementAt(offset)?.findParentOfType<KtCallExpression>() ?: return null

    callArgumentMap.map { (valueArgument, callExpression) ->
        val argumentIndex = originalCall.valueArguments.indexOf(valueArgument)
        newCall.valueArguments[argumentIndex] to callExpression
    }.forEach { (newArgument, callExpression) ->
        newArgument?.let {
            val expression = (callExpression.lambdaArguments.firstOrNull() as? KtLambdaArgument)?.getArgumentExpression() ?: return@forEach
            it.replace(psiFactory.createExpression(expression.text))
        }
    }

    return copied to newCall
}
context(_: KaSession)
private fun canBeReplaced(
    parentCall: KtCallExpression,
    samConstructorCallArgumentMap: Map<KtValueArgument, KtCallExpression>
): Boolean {
    val (newExpression, newCall) =
        createAnalyzableExpression(parentCall, samConstructorCallArgumentMap) ?: return false

    val offset = newCall.getStartOffsetIn(newExpression)

    val codeFragment = KtPsiFactory(parentCall.project)
        .createBlockCodeFragment(newExpression.text, parentCall)
    // to restore KtCallExpression at codeFragment
    val callExpression = codeFragment.findElementAt(offset)?.findParentOfType<KtCallExpression>()
    val dotQualifiedExpression = callExpression?.findTopmostParentOfType<KtDotQualifiedExpression>()
    val contentElement = dotQualifiedExpression ?: callExpression ?: return false

    analyze(contentElement) {
        val resolvedNewCall = contentElement.getPossiblyQualifiedCallExpression()?.resolveToCall()
        val singleFunctionCallOrNull = resolvedNewCall?.successfulFunctionCallOrNull()
        val newSymbol = singleFunctionCallOrNull?.partiallyAppliedSymbol?.symbol ?: return false

        val resolveToCall = parentCall.resolveToCall()
        val originalSymbol = resolveToCall?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return false
        return newSymbol.equalsOrEqualsByPsi(originalSymbol)
    }
}

@ApiStatus.Internal
fun KaSymbol?.equalsOrEqualsByPsi(other: KaSymbol?): Boolean {
    if (this == other) return true
    val thisPsi = this?.psi ?: return false
    // TODO: KT-73732 it is a known issue with comparing symbols by `==` in K1 sessions
    //  for this case, it is safe to use corresponding PSI
    return other != null && thisPsi == other.psi
}

context(_: KaSession)
@ApiStatus.Internal
fun samConstructorCallsToBeConverted(functionCall: KtCallExpression): Collection<KtCallExpression> {
    val valueArguments = functionCall.valueArguments
    if (valueArguments.none { canBeSamConstructorCall(it) }) return emptyList()

    val resolvedFunctionCall = functionCall.resolveToCall()?.successfulFunctionCallOrNull() ?: return emptyList()

    /**
     * Checks that SAM conversion for [arg] and [call] in the argument position is possible
     * and does not loose any information.
     *
     * We want to do as many cheap checks as possible before actually trying to resolve substituted call in [canBeReplaced].
     *
     * Several cases where we do not want the conversion:
     *
     * - Expected argument type is inferred from the argument; for example when the expected type is `T`, and SAM constructor
     * helps to deduce it.
     * - Expected argument type is a base type for the actual argument type; for example when expected type is `Any`, and removing
     * SAM constructor will lead to passing object of different type.
     */
    fun samConversionIsPossible(arg: KtValueArgument, call: KtCallExpression): Boolean {
        val resolvedCall = call.resolveToCall()
        val simpleFunctionCall = resolvedCall?.successfulFunctionCallOrNull() as? KaSimpleFunctionCall
        // we suppose that SAM constructors return type is always not nullable
        (simpleFunctionCall?.symbol as? KaSamConstructorSymbol)?.takeUnless { it.returnType.nullability.isNullable }
            ?: return false
        val samConstructorReturnType = simpleFunctionCall.partiallyAppliedSymbol.signature.returnType

        val argumentExpression = arg.getArgumentExpression()

        val signature = resolvedFunctionCall.argumentMapping[argumentExpression]
            ?: return false

        val signatureReturnType = signature.symbol.returnType.withNullability(false)
        // for `testData/inspectionsLocal/redundantSamConstructor/genericParameter.kt`
        // signatureReturnType is `T`, while originalParameterType is `java/lang/Runnable`
        // target function parameter could not be generic parameter
        if (signatureReturnType.symbol == null)
            return false

        val originalParameterType =
            signature.returnType.withNullability(false)
        return samConstructorReturnType.semanticallyEquals(originalParameterType)
    }

    val argumentsWithSamConstructors = valueArguments.keysToMapExceptNulls { arg ->
        arg.toCallExpression()?.takeIf { call ->
            samConversionIsPossible(arg, call)
        }
    }

    val argumentsThatCanBeConverted = argumentsWithSamConstructors.filterValues { !containsLabeledReturnPreventingConversion(it) }

    return when {
        argumentsThatCanBeConverted.isEmpty() -> emptyList()
        !canBeReplaced(functionCall, argumentsThatCanBeConverted) -> emptyList()
        else -> argumentsThatCanBeConverted.values
    }
}

private fun canBeSamConstructorCall(argument: KtValueArgument): Boolean =
    argument.toCallExpression()?.getSamConstructorValueArgument() != null

private fun ValueArgument.toCallExpression(): KtCallExpression? =
    getArgumentExpression()?.getPossiblyQualifiedCallExpression()

private fun containsLabeledReturnPreventingConversion(it: KtCallExpression): Boolean {
    val samValueArgument = it.getSamConstructorValueArgument()
    val samConstructorName = (it.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName()
    return samValueArgument == null ||
            samConstructorName == null ||
            samValueArgument.hasLabeledReturnPreventingConversion(samConstructorName)
}

private fun KtValueArgument.hasLabeledReturnPreventingConversion(samConstructorName: Name) =
    anyDescendantOfType<KtReturnExpression> { it.getLabelNameAsName() == samConstructorName }
