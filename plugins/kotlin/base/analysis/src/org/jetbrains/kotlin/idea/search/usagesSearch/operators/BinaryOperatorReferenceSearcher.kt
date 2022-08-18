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
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class BinaryOperatorReferenceSearcher(
    targetFunction: PsiElement,
    private val operationTokens: List<KtSingleValueToken>,
    searchScope: SearchScope,
    consumer: Processor<in PsiReference>,
    optimizer: SearchRequestCollector,
    options: KotlinReferencesSearchOptions
) : OperatorReferenceSearcher<KtBinaryExpression>(
    targetFunction,
    searchScope,
    consumer,
    optimizer,
    options,
    wordsToSearch = operationTokens.map { it.value }) {

    override fun processPossibleReceiverExpression(expression: KtExpression) {
        val binaryExpression = expression.parent as? KtBinaryExpression ?: return
        if (binaryExpression.operationToken !in operationTokens) return
        if (expression != binaryExpression.left) return
        processReferenceElement(binaryExpression)
    }

    override fun isReferenceToCheck(ref: PsiReference): Boolean {
        if (ref !is KtSimpleNameReference) return false
        val element = ref.element
        if (element.parent !is KtBinaryExpression) return false
        return element.getReferencedNameElementType() in operationTokens
    }

    override fun extractReference(element: KtElement): PsiReference? {
        val binaryExpression = element as? KtBinaryExpression ?: return null
        if (binaryExpression.operationToken !in operationTokens) return null
        return binaryExpression.operationReference.references.firstIsInstance<KtSimpleNameReference>()
    }
}