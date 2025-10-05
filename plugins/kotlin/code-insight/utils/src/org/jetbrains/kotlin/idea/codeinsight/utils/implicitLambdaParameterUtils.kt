// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

fun KtNameReferenceExpression.isReferenceToImplicitLambdaParameter(): Boolean {
    if (getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            return getImplicitLambdaParameterSymbol() != null
        }
    }
}

fun KtNameReferenceExpression.getFunctionLiteralByImplicitLambdaParameter(): KtFunctionLiteral? {
    if (getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return null
    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            return getImplicitLambdaParameterSymbol()?.getFunctionLiteralByImplicitLambdaParameterSymbol()
        }
    }
}


@OptIn(KaContextParameterApi::class)
context(_: KaSession)
fun KtNameReferenceExpression.getImplicitLambdaParameterSymbol(): KaValueParameterSymbol? {
    val parameterSymbol = mainReference.resolveToSymbol() as? KaValueParameterSymbol ?: return null
    if (!parameterSymbol.isImplicitLambdaParameter) return null
    return parameterSymbol
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
fun KaValueParameterSymbol.getFunctionLiteralByImplicitLambdaParameterSymbol(): KtFunctionLiteral? {
    if (!isImplicitLambdaParameter) return null
    val lambda = containingDeclaration as? KaAnonymousFunctionSymbol ?: return null
    return lambda.psi as? KtFunctionLiteral
}
