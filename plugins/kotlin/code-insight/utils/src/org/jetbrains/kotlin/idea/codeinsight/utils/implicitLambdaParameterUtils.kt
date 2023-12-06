// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

fun KtNameReferenceExpression.isReferenceToImplicitLambdaParameter(): Boolean {
    if (getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
    @OptIn(KtAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            return getImplicitLambdaParameterSymbol() != null
        }
    }
}

fun KtNameReferenceExpression.getFunctionLiteralByImplicitLambdaParameter(): KtFunctionLiteral? {
    if (getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return null
    @OptIn(KtAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            val implicitParameterSymbol = getImplicitLambdaParameterSymbol() ?: return null
            val lambda = implicitParameterSymbol.getContainingSymbol() as? KtAnonymousFunctionSymbol ?: return null
            return lambda.psi as? KtFunctionLiteral
        }
    }
}


context(KtAnalysisSession)
fun KtNameReferenceExpression.getImplicitLambdaParameterSymbol(): KtValueParameterSymbol? {
    val parameterSymbol = mainReference.resolveToSymbol() as? KtValueParameterSymbol ?: return null
    if (!parameterSymbol.isImplicitLambdaParameter) return null
    return parameterSymbol
}
