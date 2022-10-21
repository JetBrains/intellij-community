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

object KtElementParameterInfoFromKtFunctionCallProvider {
    context(KtAnalysisSession)
    fun KtElement.hasTypeMismatchBeforeCurrent(
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        currentArgumentIndex: Int,
    ): Boolean {
        val argumentExpressionsBeforeCurrent = getArgumentOrIndexExpressions().take(currentArgumentIndex + 1).filterNotNull()
        for (argumentExpression in argumentExpressionsBeforeCurrent) {
            val parameterForArgument = argumentMapping[argumentExpression] ?: continue
            val argumentType = argumentExpression.getKtType() ?: error("Argument should have a KtType")
            if (argumentType.isNotSubTypeOf(parameterForArgument.returnType)) {
                return true
            }
        }
        return false
    }

    /* Returns argument expressions mapped to parameter indices. In case of array set call the value to set is ignored. */
    context(KtAnalysisSession)
    fun KtElement.mapArgumentsToParameterIndices(
        signature: KtFunctionLikeSignature<*>,
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>
    ): Map<KtExpression, Int> {
        val isArraySetCall = isArraySetCall(signature)
        val parameterToIndex = mapParametersToIndices(signature, isArraySetCall)
        return argumentMapping.entries.mapNotNull { (argument, parameter) ->
            if (parameter in parameterToIndex) argument to parameterToIndex.getValue(parameter) else null
        }.toMap()
    }

    context(KtAnalysisSession)
    fun KtElement.isArraySetCall(signature: KtFunctionLikeSignature<*>) = signature.symbol.callableIdIfNonLocal?.let {
        val isSet = it.callableName == OperatorNameConventions.SET
        isSet && this is KtArrayAccessExpression
    } ?: false

    fun KtElement.getArgumentOrIndexExpressions(): List<KtExpression?> {
        return when (this) {
            is KtCallElement -> valueArgumentList?.arguments?.map { it.getArgumentExpression() } ?: listOf()
            is KtArrayAccessExpression -> this.indexExpressions
            else -> listOf()
        }
    }

    /**
     * "Named mode" (all arguments should be named) begins when:
     * 1. A named argument is unmapped (i.e., non-existent name).
     * 2. There is a named argument NOT in its own position.
     * 3. Mixed named arguments in their own positions are not supported.
     * 4. An argument is after non-named vararg.
     */
    context(KtAnalysisSession)
    fun KtCallElement.firstArgumentInNamedMode(
        signature: KtFunctionLikeSignature<*>,
        argumentMapping: Map<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        languageVersionSettings: LanguageVersionSettings
    ): KtValueArgument? {
        val valueArguments = valueArgumentList?.arguments ?: return null
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

    /* Returns parameters mapped to their indices. In case of array set call last parameter is ignored. */
    context(KtAnalysisSession)
    private fun mapParametersToIndices(
        signature: KtFunctionLikeSignature<*>,
        isArraySetCall: Boolean
    ): Map<KtVariableLikeSignature<KtValueParameterSymbol>, Int> {
        val valueParameters = signature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }
        return valueParameters.mapIndexed { index, parameter -> parameter to index }.toMap()
    }
}