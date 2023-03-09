/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

object CallParameterInfoProvider {
    /**
     * Returns true when there is an argument before current that is mapped to a parameter with different type.
     */
    context(KtAnalysisSession)
    fun hasTypeMismatchBeforeCurrent(
        sourceElement: KtElement,
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        currentArgumentIndex: Int,
    ): Boolean {
        val argumentExpressionsBeforeCurrent = getArgumentOrIndexExpressions(sourceElement).take(currentArgumentIndex).filterNotNull()
        for (argumentExpression in argumentExpressionsBeforeCurrent) {
            val parameterForArgument = argumentMapping[argumentExpression] ?: continue
            val argumentType = argumentExpression.getKtType() ?: error("Argument should have a KtType")
            if (argumentType.isNotSubTypeOf(parameterForArgument.returnType)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns argument expressions mapped to parameter indices. In case of array set call the value to set is ignored.
     */
    context(KtAnalysisSession)
    fun mapArgumentsToParameterIndices(
        sourceElement: KtElement,
        signature: KtFunctionLikeSignature<*>,
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>
    ): Map<KtExpression, Int> {
        val isArraySetCall = isArraySetCall(sourceElement, signature)
        val parameterToIndex = mapParametersToIndices(signature, isArraySetCall)
        return argumentMapping.mapNotNull { (argument, parameter) ->
            if (parameter in parameterToIndex) argument to parameterToIndex.getValue(parameter) else null
        }.toMap()
    }

    /**
     * Returns true in case of array access which resolves to set operator.
     * ```
     * class A {
     *     operator fun set(x: String, value: Int) {}
     * }
     * val a = A()
     * a[""] = 1 // array set call
     * ```
     */
    context(KtAnalysisSession)
    fun isArraySetCall(sourceElement: KtElement, signature: KtFunctionLikeSignature<*>): Boolean {
        val callableId = signature.symbol.callableIdIfNonLocal ?: return false
        val isSet = callableId.callableName == OperatorNameConventions.SET
        return isSet && sourceElement is KtArrayAccessExpression
    }

    fun getArgumentOrIndexExpressions(sourceElement: KtElement): List<KtExpression?> = when (sourceElement) {
        is KtCallElement -> sourceElement.valueArgumentList?.arguments?.map { it.getArgumentExpression() }.orEmpty()
        is KtArrayAccessExpression -> sourceElement.indexExpressions
        else -> listOf()
    }

    /**
     * "Named mode" (all arguments should be named) begins when:
     * 1. A named argument is unmapped (i.e., non-existent name).
     * 2. There is a named argument NOT in its own position.
     * 3. Mixed named arguments in their own positions are not supported.
     * 4. An argument is after non-named vararg.
     */
    context(KtAnalysisSession)
    fun firstArgumentInNamedMode(
        sourceCallElement: KtCallElement,
        signature: KtFunctionLikeSignature<*>,
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        languageVersionSettings: LanguageVersionSettings
    ): KtValueArgument? {
        val valueArguments = sourceCallElement.valueArgumentList?.arguments ?: return null
        val parameterToIndex = mapParametersToIndices(signature, false)
        val supportsMixedNamedArguments = languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)

        var afterNonNamedVararg = false

        for ((argumentIndex, valueArgument) in valueArguments.withIndex()) {
            val parameter = argumentMapping[valueArgument.getArgumentExpression()]
            val parameterIndex = parameterToIndex[parameter]
            val isVararg = parameter?.symbol?.isVararg

            if (valueArgument.isNamed()) {
                if (parameterIndex == null || parameterIndex != argumentIndex || !supportsMixedNamedArguments) return valueArgument
            }
            if (isVararg == false && afterNonNamedVararg) return valueArgument

            if (isVararg == true && !valueArgument.isNamed()) afterNonNamedVararg = true
        }
        return null
    }

    /**
     * Returns parameters mapped to their indices. In case of array set call last parameter is ignored.
     */
    context(KtAnalysisSession)
    private fun mapParametersToIndices(
        signature: KtFunctionLikeSignature<*>,
        isArraySetCall: Boolean
    ): Map<KtVariableLikeSignature<KtValueParameterSymbol>, Int> {
        val valueParameters = signature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }
        return valueParameters.mapIndexed { index, parameter -> parameter to index }.toMap()
    }
}