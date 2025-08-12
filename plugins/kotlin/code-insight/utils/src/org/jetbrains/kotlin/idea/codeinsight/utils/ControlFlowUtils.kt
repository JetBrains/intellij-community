// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

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

fun findRelevantLoopForExpression(expression: KtExpression): KtLoopExpression? {
    val expressionLabelName = when (expression) {
        is KtExpressionWithLabel -> expression.getLabelName()
        is KtLoopExpression -> (expression.parent as? KtLabeledExpression)?.getLabelName()
        else -> null
    }

    for (loopExpression in expression.parentsOfType<KtLoopExpression>(withSelf = true)) {
        if (expressionLabelName == null || (loopExpression.parent as? KtLabeledExpression)?.getLabelName() == expressionLabelName) {
            return loopExpression
        }
    }

    return null
}

fun KtNamedFunction.isRecursive(): Boolean {
    return bodyExpression?.includesCallOf(this) == true
}

fun canExplicitTypeBeRemoved(element: KtDeclaration): Boolean {
    val typeReference = element.typeReference ?: return false

    fun canBeRemovedByTypeReference(element: KtDeclaration, typeReference: KtTypeReference): Boolean =
        !typeReference.isAnnotatedDeep() && !element.isExplicitTypeReferenceNeededForTypeInferenceByPsi(typeReference)

    return when {
        !canBeRemovedByTypeReference(element, typeReference) -> false
        element is KtParameter -> element.isLoopParameter || element.isSetterParameter
        element is KtNamedFunction -> true
        element is KtProperty || element is KtPropertyAccessor -> element.getInitializerOrGetterInitializer() != null
        else -> false
    }
}

val KtDeclaration.typeReference: KtTypeReference?
    get() = when (this) {
        is KtCallableDeclaration -> typeReference
        is KtPropertyAccessor -> typeReference
        else -> null
    }


private fun KtExpression.includesCallOf(function: KtNamedFunction): Boolean {
    val refDescriptor = mainReference?.resolve()
    return function == refDescriptor || anyDescendantOfType<KtExpression> {
        it !== this && it !is KtLabelReferenceExpression && function == it.mainReference?.resolve()
    }
}
