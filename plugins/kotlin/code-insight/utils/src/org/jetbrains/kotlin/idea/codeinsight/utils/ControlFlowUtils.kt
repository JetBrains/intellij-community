// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
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

/**
 * Finds the nearest loop expression that contains the given expression, taking into account any labels
 * and outer loops.
 *
 * Returns null if no relevant loop is found.
 */
fun findRelevantLoopForExpression(expression: KtExpression): KtLoopExpression? {
    val expressionLabelName = when (expression) {
        is KtExpressionWithLabel -> expression.getLabelName()
        is KtLoopExpression -> (expression.parent as? KtLabeledExpression)?.getLabelName()
        else -> null
    }

    for (loopExpression in expression.parentsOfType<KtLoopExpression>(withSelf = true)) {
        if (loopExpression == expression)
            return loopExpression

        if (expressionLabelName != null && (loopExpression.parent as? KtLabeledExpression)?.getLabelName() == expressionLabelName)
            return loopExpression

        if (expressionLabelName == null && expression.doesBelongToLoop(loopExpression))
            return loopExpression
    }

    return null
}

@ApiStatus.Internal
fun KtExpression.doesBelongToLoop(loopExpression: KtExpression): Boolean {
    val allowNonLocalBreaks =
        loopExpression.module?.languageVersionSettings?.supportsFeature(LanguageFeature.BreakContinueInInlineLambdas) == true
    val structureBodies = PsiTreeUtil.collectParents(
        /* element = */ this,
        /* parent = */ KtContainerNodeForControlStructureBody::class.java,
        /* includeMyself = */ false
    ) {
        when(val p = it.parent) {
            is KtProperty if p.isLocal -> false
            is KtDeclaration -> !(allowNonLocalBreaks && p is KtFunctionLiteral)
            else -> false
        }
    }
    // expression belongs to the loop when it is inside the loop body
    return structureBodies.firstOrNull { it.parent is KtLoopExpression }?.parent == loopExpression
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
