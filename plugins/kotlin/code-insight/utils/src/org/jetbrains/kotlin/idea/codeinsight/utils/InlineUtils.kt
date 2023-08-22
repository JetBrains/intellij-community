// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.getContainingValueArgument
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

context(KtAnalysisSession)
@ApiStatus.Internal
fun isInlinedArgument(argument: KtFunction): Boolean = getInlineArgumentSymbol(argument) != null

context(KtAnalysisSession)
@ApiStatus.Internal
fun getInlineArgumentSymbol(argument: KtFunction): KtValueParameterSymbol? {
    if (argument !is KtFunctionLiteral && argument !is KtNamedFunction) return null

    val parentCallExpression = KtPsiUtil.getParentCallIfPresent(argument) as? KtCallExpression ?: return null
    val parentCall = parentCallExpression.resolveCall()?.successfulFunctionCallOrNull() ?: return null
    val symbol = parentCall.partiallyAppliedSymbol.symbol

    if ((symbol is KtFunctionSymbol && symbol.isInline) || isArrayGeneratorConstructorCall(symbol)) {
        val valueArgument = parentCallExpression.getContainingValueArgument(argument) ?: return null
        val argumentSymbol = parentCall.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return null
        if (argumentSymbol.isNoinline) return null
        val parameterType = argumentSymbol.returnType
        if (!parameterType.isMarkedNullable
               && (parameterType.isFunctionType || parameterType.isSuspendFunctionType)) {
            return argumentSymbol
        }
    }

    return null
}

context(KtAnalysisSession)
private fun isArrayGeneratorConstructorCall(symbol: KtFunctionLikeSymbol): Boolean {
    fun checkParameters(symbol: KtFunctionLikeSymbol): Boolean {
        return symbol.valueParameters.size == 2
                && symbol.valueParameters[0].returnType.isInt
                && symbol.valueParameters[1].returnType.isFunctionType
    }

    if (symbol is KtConstructorSymbol) {
        val classId = symbol.containingClassIdIfNonLocal
        val isArrayClass = classId == StandardClassIds.Array
                || classId in StandardClassIds.elementTypeByPrimitiveArrayType
                || classId in StandardClassIds.elementTypeByUnsignedArrayType

        return isArrayClass && checkParameters(symbol)
    } else if (symbol is KtFunctionSymbol && symbol.isExtension) {
        val receiverType = symbol.receiverType
        return receiverType is KtNonErrorClassType
                && receiverType.classId in StandardClassIds.elementTypeByUnsignedArrayType
                && symbol.callableIdIfNonLocal?.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME
                && checkParameters(symbol)
    }

    return false
}