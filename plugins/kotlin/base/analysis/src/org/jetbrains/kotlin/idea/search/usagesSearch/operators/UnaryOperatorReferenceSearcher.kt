// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.usagesSearch.operators

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class UnaryOperatorReferenceSearcher(
    targetFunction: PsiElement,
    private val operationToken: KtSingleValueToken,
    searchScope: SearchScope,
    consumer: Processor<in PsiReference>,
    optimizer: SearchRequestCollector,
    options: KotlinReferencesSearchOptions
) : OperatorReferenceSearcher<KtUnaryExpression>(
    targetFunction,
    searchScope,
    consumer,
    optimizer,
    options,
    wordsToSearch = listOf(operationToken.value)
) {

    override fun processPossibleReceiverExpression(expression: KtExpression) {
        val unaryExpression = expression.parent as? KtUnaryExpression ?: return
        if (unaryExpression.operationToken != operationToken) return
        processReferenceElement(unaryExpression)
    }

    override fun isReferenceToCheck(ref: PsiReference): Boolean {
        if (ref !is KtSimpleNameReference) return false
        val element = ref.element
        if (element.parent !is KtUnaryExpression) return false
        return element.getReferencedNameElementType() == operationToken
    }

    override fun extractReference(element: KtElement): PsiReference? {
        val unaryExpression = element as? KtUnaryExpression ?: return null
        if (unaryExpression.operationToken != operationToken) return null
        return unaryExpression.operationReference.references.firstIsInstance<KtSimpleNameReference>()
    }
}