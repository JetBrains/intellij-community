// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

fun KtExpression.isComplexInitializer(): Boolean {
    fun KtExpression.isElvisExpression(): Boolean = this is KtBinaryExpression && operationToken == KtTokens.ELVIS

    if (!isOneLiner()) return true
    return anyDescendantOfType<KtExpression> {
        it is KtThrowExpression || it is KtReturnExpression || it is KtBreakExpression ||
                it is KtContinueExpression || it is KtIfExpression || it is KtWhenExpression ||
                it is KtTryExpression || it is KtLambdaExpression || it.isElvisExpression()
    }
}


fun KtCallableDeclaration.hasUsages(inElement: KtElement): Boolean {
    assert(inElement.isPhysical)
    return hasUsages(listOf(inElement))
}

fun KtCallableDeclaration.hasUsages(inElements: Collection<KtElement>): Boolean {
    assert(this.isPhysical)
    return ReferencesSearch.search(this, LocalSearchScope(inElements.toTypedArray())).any()
}

fun KtExpression.isExitStatement(): Boolean =
    this is KtContinueExpression || this is KtBreakExpression || this is KtThrowExpression || this is KtReturnExpression
