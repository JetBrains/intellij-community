// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.resolveSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.name.JvmStandardClassIds.TRANSIENT_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object ReturnTypeNullabilityUtil {
    context(_: KaSession)
    fun hasOnlyNonNullableReturns(element: KtCallableDeclaration): Boolean {
        if (element.isOverridable() || hasJvmTransientAnnotation(element)) return false

        val actualReturnTypes = when (element) {
            is KtNamedFunction -> {
                val bodyExpression = element.bodyExpression ?: return false
                val actualReturnTypes = actualReturnTypes(bodyExpression, element)
                actualReturnTypes
            }

            is KtProperty -> {
                val initializer = element.initializer
                val getter = element.accessors.singleOrNull { it.isGetter }
                val getterBody = getter?.bodyExpression

                buildList {
                    if (initializer != null) addAll(actualReturnTypes(initializer, element))
                    if (getterBody != null) addAll(actualReturnTypes(getterBody, getter))
                }
            }

            else -> return false
        }

        if (actualReturnTypes.isEmpty() || actualReturnTypes.any { it.isNullable }) return false

        return true
    }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun actualReturnTypes(
    expression: KtExpression,
    declaration: KtDeclaration,
): List<KaType> {
    val returnTypes = expression.collectDescendantsOfType<KtReturnExpression> {
        it.resolveSymbol() == declaration.symbol
    }.map {
        it.returnedExpression?.expressionType
    }

    return if (expression is KtBlockExpression) {
        returnTypes
    } else {
        returnTypes + expression.expressionType
    }.filterNotNull()
}

context(_: KaSession)
private fun hasJvmTransientAnnotation(declaration: KtCallableDeclaration): Boolean {
    val symbol = (declaration.symbol as? KaPropertySymbol)?.backingFieldSymbol ?: return false
    return symbol.annotations.any {
        it.classId?.asSingleFqName() == TRANSIENT_ANNOTATION_FQ_NAME
    }
}
