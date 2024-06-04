// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.util.OperatorNameConventions

fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    if (callExpression.valueArguments.isNotEmpty()) return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != OperatorNameConventions.TO_STRING.asString()) return false
    return analyze(callExpression) {
        referenceExpression.mainReference.resolveToSymbols().any { symbol ->
            val functionSymbol = symbol as? KtFunctionSymbol ?: return@any false
            functionSymbol.valueParameters.isEmpty() && functionSymbol.returnType.isString
        }
    }
}

context(KtAnalysisSession)
fun KtDeclaration.isFinalizeMethod(): Boolean {
    if (containingClass() == null) return false
    val function = this as? KtNamedFunction ?: return false
    return function.name == "finalize"
            && function.valueParameters.isEmpty()
            && function.getReturnKtType().isUnit
}

context(KtAnalysisSession)
fun KtSymbol.getFqNameIfPackageOrNonLocal(): FqName? = when (this) {
    is KtPackageSymbol -> fqName
    is KtCallableSymbol -> callableId?.asSingleFqName()
    is KtClassLikeSymbol -> classId?.asSingleFqName()
    else -> null
}

context(KtAnalysisSession)
fun KtCallExpression.isArrayOfFunction(): Boolean {
    val functionNames = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values.toSet() +
            ArrayFqNames.ARRAY_OF_FUNCTION +
            ArrayFqNames.EMPTY_ARRAY

    val call = resolveCallOld()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.callableId ?: return false

    return call.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
            functionNames.contains(call.callableName)
}

/**
 * Determines whether [this] call expression is an implicit `invoke` operator call.
 *
 * @return `true` if the expression is an implicit `invoke` call, `false` if it is not,
 * and `null` if the function resolve was unsuccessful.
 */
context(KtAnalysisSession)
fun KtCallExpression.isImplicitInvokeCall(): Boolean? {
    val functionCall = this.resolveCallOld()?.singleFunctionCallOrNull() ?: return null

    return functionCall is KtSimpleFunctionCall && functionCall.isImplicitInvoke
}

