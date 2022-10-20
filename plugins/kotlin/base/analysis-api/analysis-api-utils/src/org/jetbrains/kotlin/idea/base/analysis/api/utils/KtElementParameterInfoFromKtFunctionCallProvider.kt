/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
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
        val valueParameters = signature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }
        val parameterToIndex = valueParameters.mapIndexed { index, parameter -> parameter to index }.toMap()
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
}