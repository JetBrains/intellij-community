// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.getContainingValueArgument
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

context(KaSession)
@ApiStatus.Internal
fun isInlinedArgument(argument: KtFunction): Boolean = getInlineArgumentSymbol(argument) != null

context(KaSession)
@ApiStatus.Internal
fun getInlineArgumentSymbol(argument: KtExpression): KaValueParameterSymbol? {
    if (argument !is KtFunctionLiteral && argument !is KtNamedFunction && argument !is KtCallableReferenceExpression) return null

    val (symbol, argumentSymbol) = getCallExpressionSymbol(argument)
        ?: getDefaultArgumentSymbol(argument)
        ?: return null

    if ((symbol is KaNamedFunctionSymbol && symbol.isInline) || isArrayGeneratorConstructorCall(symbol)) {
        if (argumentSymbol.isNoinline) return null
        val parameterType = argumentSymbol.returnType
        if (!parameterType.isMarkedNullable
               && (parameterType.isFunctionType || parameterType.isSuspendFunctionType)) {
            return argumentSymbol
        }
    }

    return null
}


context(KaSession)
@ApiStatus.Internal
fun getFunctionSymbol(argument: KtExpression): KaFunctionSymbol? = getCallExpressionSymbol(argument)?.first
    ?: getDefaultArgumentSymbol(argument)?.first

context(KaSession)
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

context(KaSession)
@ApiStatus.Internal
fun getCallExpressionSymbol(argument: KtExpression): Pair<KaFunctionSymbol, KaValueParameterSymbol>? {
    if (argument !is KtFunction && argument !is KtCallableReferenceExpression) return null
    val parentCallExpression = KtPsiUtil.getParentCallIfPresent(argument) as? KtCallExpression ?: return null
    val parentCall = resolveFunctionCall(parentCallExpression) ?: return null
    val symbol = parentCall.partiallyAppliedSymbol.symbol
    val valueArgument = parentCallExpression.getContainingValueArgument(argument) ?: return null
    val argumentSymbol = parentCall.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return null
    return symbol to argumentSymbol
}

context(KaSession)
@ApiStatus.Internal
fun resolveFunctionCall(expression: KtExpression): KaFunctionCall<*>? {
    val successfulCall = expression.resolveToCall()?.successfulFunctionCallOrNull()
    if (successfulCall != null) return successfulCall
    if (!ApplicationManager.getApplication().isUnitTestMode) return null
    // Functions with context receivers are not resolved in K2 tests for some reason
    return expression.resolveToCallCandidates().firstOrNull()?.candidate as? KaFunctionCall<*>
}

context(KaSession)
private fun isArrayGeneratorConstructorCall(symbol: KaFunctionSymbol): Boolean {
    fun checkParameters(symbol: KaFunctionSymbol): Boolean {
        return symbol.valueParameters.size == 2
                && symbol.valueParameters[0].returnType.isIntType
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