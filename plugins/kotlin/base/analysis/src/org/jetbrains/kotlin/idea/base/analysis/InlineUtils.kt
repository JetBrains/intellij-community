// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

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

@ApiStatus.Internal
fun KtAnalysisSession.isInlinedArgument(argument: KtFunction, checkNonLocalReturn: Boolean): Boolean {
    val parameterSymbol = getInlineArgumentSymbol(argument) ?: return false
    if (parameterSymbol.isNoinline || (checkNonLocalReturn && parameterSymbol.isCrossinline)) {
        return false
    }

    val parameterType = parameterSymbol.returnType
    return !parameterType.isMarkedNullable
            && (parameterType.isFunctionType || parameterType.isSuspendFunctionType)
}

private fun KtAnalysisSession.getInlineArgumentSymbol(argument: KtFunction): KtValueParameterSymbol? {
    if (argument !is KtFunctionLiteral && argument !is KtNamedFunction) return null

    val parentCallExpression = KtPsiUtil.getParentCallIfPresent(argument) as? KtCallExpression ?: return null
    val parentCall = parentCallExpression.resolveCall().successfulFunctionCallOrNull() ?: return null
    val symbol = parentCall.partiallyAppliedSymbol.symbol

    if ((symbol is KtFunctionSymbol && symbol.isInline) || isArrayGeneratorConstructorCall(symbol)) {
        val valueArgument = parentCallExpression.getContainingValueArgument(argument) ?: return null
        return parentCall.argumentMapping[valueArgument.getArgumentExpression()]?.symbol
    }

    return null
}

private fun KtAnalysisSession.isArrayGeneratorConstructorCall(symbol: KtFunctionLikeSymbol): Boolean {
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