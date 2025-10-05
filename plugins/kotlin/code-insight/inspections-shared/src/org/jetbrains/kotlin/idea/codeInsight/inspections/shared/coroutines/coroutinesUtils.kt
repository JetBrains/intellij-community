// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findTopLevelCallables
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.equalsOrEqualsByPsi
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.singleStatementOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Finds a single (implicitly or explicitly) returned expression from [lambdaExpression].
 */
internal fun KaSession.singleReturnedExpressionOrNull(lambdaExpression: KtLambdaExpression): KtExpression? {
    val singleStatement = lambdaExpression.singleStatementOrNull() ?: return null

    return when (singleStatement) {
        is KtReturnExpression if (singleStatement.targetSymbol == lambdaExpression.functionLiteral.symbol) -> singleStatement.returnedExpression
        else -> singleStatement
    }
}

/**
 * Checks if a lambda has a single parameter and calls a specific method on it.
 *
 * @param lambdaExpression The lambda expression to check
 * @param callableId The method that should be called on the parameter
 * @return true if the lambda has a single parameter and calls the specified method on it
 */
internal fun KaSession.isLambdaWithSingleReturnedCallOnSingleParameter(
    lambdaExpression: KtLambdaExpression,
    callableId: CallableId
): Boolean {
    val singleLambdaParameterSymbol = lambdaExpression.functionLiteral.symbol.valueParameters.singleOrNull() ?: return false
    val singleReturnedExpression = singleReturnedExpressionOrNull(lambdaExpression) as? KtDotQualifiedExpression ?: return false

    val methodCall = singleReturnedExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false

    val explicitReceiverValue = methodCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue ?: return false
    val explicitReceiverAccessCall = explicitReceiverValue.expression.resolveToCall()?.successfulVariableAccessCall() ?: return false

    return methodCall.symbol.callableId == callableId &&
            explicitReceiverAccessCall.symbol == singleLambdaParameterSymbol
}

/**
 * Checks if a package is already imported via star import.
 */
// TODO Use Import insertion API after KTIJ-28838 is fixed
internal fun isPackageImportedByStarImport(file: KtFile, packageFqName: FqName): Boolean {
    return file.importDirectives.any {
        it.importPath == ImportPath(packageFqName, isAllUnder = true)
    }
}

internal fun KaSession.isIterableForEachFunctionCall(element: KtCallExpression): Boolean {
    val functionCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
    val actualReceiverType = functionCall.partiallyAppliedSymbol.extensionReceiver?.type ?: return false

    return isIterableForEachFunction(functionCall.symbol) &&
            actualReceiverType.isSubtypeOf(StandardClassIds.Collection)
}

private fun KaSession.isIterableForEachFunction(symbol: KaFunctionSymbol): Boolean {
    return symbol.callableId == KOTLIN_COLLECTIONS_FOR_EACH_ID &&
            symbol.receiverParameter?.returnType?.isSubtypeOf(StandardClassIds.Iterable) == true
}

internal fun KaSession.isIterableMapFunctionCall(element: KtCallExpression): Boolean {
    val functionCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
    val actualReceiverType = functionCall.partiallyAppliedSymbol.extensionReceiver?.type ?: return false

    return isIterableMapFunction(functionCall.symbol) &&
            actualReceiverType.isSubtypeOf(StandardClassIds.Collection)
}

private fun KaSession.isIterableMapFunction(symbol: KaFunctionSymbol): Boolean {
    return symbol.callableId == KOTLIN_COLLECTIONS_MAP_ID &&
            symbol.receiverParameter?.returnType?.isSubtypeOf(StandardClassIds.Iterable) == true
}

// Common collection-related CallableIds
internal val KOTLIN_COLLECTIONS_FOR_EACH_ID: CallableId = CallableId(FqName("kotlin.collections"), Name.identifier("forEach"))
internal val KOTLIN_COLLECTIONS_MAP_ID: CallableId = CallableId(FqName("kotlin.collections"), Name.identifier("map"))

context(_: KaSession)
internal fun KtFunction.findRelatedLabelReferences(): List<KtLabelReferenceExpression> {
    val functionSymbol = symbol
    
    return descendantsOfType<KtLabelReferenceExpression>()
        .filter { labelRef ->
            labelRef.resolveExpression().equalsOrEqualsByPsi(functionSymbol)
        }
        .toList()
}

context(_: KaSession)
internal fun CallableId.canBeResolved(): Boolean {
    // does not support non-top-level callables for now
    if (this.classId != null) return false
    
    return findTopLevelCallables(packageName, callableName).any()
}