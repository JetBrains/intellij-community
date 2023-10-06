// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * Consider a property initialization `val f: (Int) -> Unit = { println(it) }`. The type annotation `(Int) -> Unit` in this case is required
 * in order for the code to type check because otherwise the compiler cannot infer the type of `it`.
 */
fun KtDeclaration.isExplicitTypeReferenceNeededForTypeInferenceByPsi(typeReference: KtTypeReference): Boolean {
    val initializer = getInitializerOrGetterInitializer() ?: return false
    if (initializer !is KtLambdaExpression && initializer !is KtNamedFunction) return false
    if (typeReference.hasModifier(KtTokens.SUSPEND_KEYWORD)) return true
    val typeElement = typeReference.typeElement as? KtFunctionType ?: return false

    if (initializer is KtLambdaExpression && typeElement.receiver != null) return true
    if (typeElement.returnTypeReference?.typeElement?.typeArgumentsAsTypes?.isNotEmpty() == true) return true
    if (typeElement.parameters.isEmpty()) return false
    val valueParameters = when (initializer) {
        is KtLambdaExpression -> initializer.valueParameters
        is KtNamedFunction -> initializer.valueParameters
        else -> emptyList()
    }
    return valueParameters.isEmpty() || valueParameters.any { it.typeReference == null }
}

/**
 * Has early return if [isExplicitTypeReferenceNeededForTypeInferenceByPsi] returns true.
 * Otherwise, checks if explicit type reference is resolved to type alias and the function needs to be invoked recursively.
 */
tailrec fun KtDeclaration.isExplicitTypeReferenceNeededForTypeInference(typeReference: KtTypeReference): Boolean {
    if (isExplicitTypeReferenceNeededForTypeInferenceByPsi(typeReference)) return true

    val typeElement = typeReference.typeElement as? KtUserType ?: return false

    val typeAlias = typeElement.referenceExpression?.mainReference?.resolve() as? KtTypeAlias ?: return false
    val typeAliasTypeReference = typeAlias.getTypeReference() ?: return false
    return isExplicitTypeReferenceNeededForTypeInference(typeAliasTypeReference)
}

fun KtDeclaration.getInitializerOrGetterInitializer(): KtExpression? {
    if (this is KtDeclarationWithInitializer && initializer != null) return initializer
    return (this as? KtProperty)?.getter?.initializer
}