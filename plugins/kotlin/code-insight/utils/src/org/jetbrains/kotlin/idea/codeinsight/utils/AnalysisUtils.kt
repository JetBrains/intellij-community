// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != OperatorNameConventions.TO_STRING.asString()) return false
    return analyze(callExpression) {
        val symbol = referenceExpression.mainReference.resolveToSymbol() as? KtFunctionSymbol ?: return false
        symbol.isMemberFunctionOfKotlinAny() || symbol.overridesMemberFunctionOfKotlinAny()
    }
}

/**
 * Returns true if the given [KtFunctionSymbol] is a member function of `kotlin.Any` built-in class.
 */
context(KtAnalysisSession)
private fun KtFunctionSymbol.isMemberFunctionOfKotlinAny(): Boolean {
    val containingClass = getContainingSymbol() as? KtClassOrObjectSymbol
    return containingClass?.classIdIfNonLocal?.asSingleFqName() == StandardNames.FqNames.any.toSafe()
}

/**
 * Returns true if the given [KtFunctionSymbol] overrides a member function of `kotlin.Any` built-in class.
 */
context(KtAnalysisSession)
private fun KtFunctionSymbol.overridesMemberFunctionOfKotlinAny(): Boolean = getAllOverriddenSymbols().any { overriddenFunctionSymbol ->
    val classContainingOverriddenFunction = overriddenFunctionSymbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return@any false
    classContainingOverriddenFunction.classIdIfNonLocal?.asSingleFqName() == StandardNames.FqNames.any.toSafe()
}