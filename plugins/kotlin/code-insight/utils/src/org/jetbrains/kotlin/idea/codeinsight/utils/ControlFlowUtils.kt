// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * Consider a property initialization `val f: (Int) -> Unit = { println(it) }`. The type annotation `(Int) -> Unit` in this case is required
 * in order for the code to type check because otherwise the compiler cannot infer the type of `it`.
 */
tailrec fun KtCallableDeclaration.isExplicitTypeReferenceNeededForTypeInference(typeRef: KtTypeReference? = typeReference): Boolean {
    if (this !is KtDeclarationWithInitializer) return false
    val initializer = initializer
    if (initializer == null || typeRef == null) return false
    if (initializer !is KtLambdaExpression && initializer !is KtNamedFunction) return false
    val typeElement = typeRef.typeElement ?: return false
    if (typeRef.hasModifier(KtTokens.SUSPEND_KEYWORD)) return true
    return when (typeElement) {
        is KtFunctionType -> {
            if (typeElement.receiver != null) return true
            if (typeElement.returnTypeReference?.typeElement?.typeArgumentsAsTypes?.isNotEmpty() == true) return true
            if (typeElement.parameters.isEmpty()) return false
            val valueParameters = when (initializer) {
                is KtLambdaExpression -> initializer.valueParameters
                is KtNamedFunction -> initializer.valueParameters
                else -> emptyList()
            }
            valueParameters.isEmpty() || valueParameters.any { it.typeReference == null }
        }
        is KtUserType -> {
            val typeAlias = typeElement.referenceExpression?.mainReference?.resolve() as? KtTypeAlias ?: return false
            return isExplicitTypeReferenceNeededForTypeInference(typeAlias.getTypeReference())
        }
        else -> false
    }
}