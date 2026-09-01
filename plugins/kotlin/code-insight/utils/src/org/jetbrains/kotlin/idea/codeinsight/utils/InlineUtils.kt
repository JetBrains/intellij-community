// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.collectCallCandidates
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isFunctionType
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.isSuspendFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.getContainingValueArgument
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolution.KtResolvableCall

@ApiStatus.Internal
context(session: KaSession)
fun isInlinedArgument(argument: KtFunction, allowCrossinline: Boolean = true): Boolean =
    getInlineArgumentSymbol(argument, allowCrossinline) != null

@ApiStatus.Internal
context(session: KaSession)
fun getInlineArgumentSymbol(argument: KtExpression, allowCrossinline: Boolean = true): KaValueParameterSymbol? {
    if (argument !is KtFunctionLiteral && argument !is KtNamedFunction && argument !is KtCallableReferenceExpression) return null

    val (symbol, argumentSymbol) = getCallExpressionSymbol(argument)
        ?: getDefaultArgumentSymbol(argument)
        ?: return null

    if ((symbol is KaNamedFunctionSymbol && symbol.isInline) || isArrayGeneratorConstructorCall(symbol)) {
        if (argumentSymbol.isNoinline) return null
        if (!allowCrossinline && argumentSymbol.isCrossinline) return null
        val parameterType = argumentSymbol.returnType
        if (!parameterType.isMarkedNullable
               && (parameterType.isFunctionType || parameterType.isSuspendFunctionType)) {
            return argumentSymbol
        }
    }

    return null
}


@ApiStatus.Internal
context(session: KaSession)
fun getFunctionSymbol(argument: KtExpression): KaFunctionSymbol? = getCallExpressionSymbol(argument)?.first
    ?: getDefaultArgumentSymbol(argument)?.first

context(session: KaSession)
private fun getDefaultArgumentSymbol(argument: KtExpression): Pair<KaFunctionSymbol, KaValueParameterSymbol>? {
    if (argument !is KtFunction && argument !is KtCallableReferenceExpression) return null
    val parameter = argument.parentOfType<KtParameter>() ?: return null
    val lambdaExpression = argument.parent as? KtLambdaExpression ?: return null
    if (parameter.defaultValue != lambdaExpression) return null
    val function = parameter.parentOfType<KtNamedFunction>() ?: return null
    val symbol = function.symbol
    val argumentSymbol = parameter.symbol as? KaValueParameterSymbol ?: return null
    return symbol to argumentSymbol
}

@ApiStatus.Internal
context(session: KaSession)
fun getCallExpressionSymbol(argument: KtExpression): Pair<KaFunctionSymbol, KaValueParameterSymbol>? {
    if (argument !is KtFunction && argument !is KtCallableReferenceExpression) return null
    val parentCallExpression = KtPsiUtil.getParentCallIfPresent(argument) as? KtCallExpression ?: return null
    val parentCall = resolveFunctionCall(parentCallExpression) ?: return null
    val symbol = parentCall.symbol
    val valueArgument = parentCallExpression.getContainingValueArgument(argument) ?: return null
    val argumentSymbol = parentCall.valueArgumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return null
    return symbol to argumentSymbol
}

@OptIn(KaExperimentalApi::class)
@ApiStatus.Internal
context(session: KaSession)
fun resolveFunctionCall(expression: KtExpression): KaFunctionCall<*>? {
    val resolvableCall = expression as? KtResolvableCall ?: return null
    resolvableCall.resolveSuccessfulCall()?.function?.let { return it }
    if (!ApplicationManager.getApplication().isUnitTestMode) return null
    // Functions with context receivers are not resolved in K2 tests for some reason
    return resolvableCall.collectCallCandidates().firstOrNull()?.candidate?.function
}

context(session: KaSession)
private fun isArrayGeneratorConstructorCall(symbol: KaFunctionSymbol): Boolean {
    fun checkParameters(symbol: KaFunctionSymbol): Boolean {
        return symbol.valueParameters.size == 2
                && symbol.valueParameters[0].returnType.classId == KaStandardTypeClassIds.INT
                && symbol.valueParameters[1].returnType.isFunctionType
    }

    if (symbol is KaConstructorSymbol) {
        val classId = symbol.containingClassId
        val isArrayClass = classId == StandardClassIds.Array
                || classId in StandardClassIds.elementTypeByPrimitiveArrayType
                || classId in StandardClassIds.elementTypeByUnsignedArrayType

        return isArrayClass && checkParameters(symbol)
    } else if (symbol is KaNamedFunctionSymbol && symbol.isExtension) {
        val receiverType = symbol.receiverType
        return receiverType is KaClassType
                && receiverType.classId in StandardClassIds.elementTypeByUnsignedArrayType
                && symbol.callableId?.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME
                && checkParameters(symbol)
    }

    return false
}
