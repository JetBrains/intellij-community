// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.util.OperatorNameConventions

fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != OperatorNameConventions.TO_STRING.asString()) return false
    return analyze(callExpression) {
        referenceExpression.mainReference.resolveToSymbols().any { symbol ->
            val functionSymbol = symbol as? KtFunctionSymbol ?: return@any false
            val callableId = functionSymbol.callableIdIfNonLocal ?: return@any false
            callableId.classId?.asSingleFqName() == StandardNames.FqNames.any.toSafe()
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
