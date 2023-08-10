// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.match

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
fun KtValueArgument.getValueArgumentName(): Name? {
    val callElement = this.parents.match(KtValueArgumentList::class, last = KtCallElement::class) ?: return null
    return analyze(callElement) {
        val resolvedCall = callElement.resolveCall()?.singleFunctionCallOrNull() ?: return null
        if (!resolvedCall.symbol.hasStableParameterNames) {
            null
        } else {
            getArgumentNameIfCanBeUsedForCalls(this@getValueArgumentName, resolvedCall)
        }
    }
}
context(KtAnalysisSession)
fun KtSymbol.getFqNameIfPackageOrNonLocal(): FqName? = when (this) {
    is KtPackageSymbol -> fqName
    is KtCallableSymbol -> callableIdIfNonLocal?.asSingleFqName()
    is KtClassLikeSymbol -> classIdIfNonLocal?.asSingleFqName()
    else -> null
}