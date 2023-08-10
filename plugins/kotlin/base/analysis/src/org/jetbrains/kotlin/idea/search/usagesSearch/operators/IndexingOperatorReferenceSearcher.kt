// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.usagesSearch.operators

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class IndexingOperatorReferenceSearcher(
    targetFunction: PsiElement,
    searchScope: SearchScope,
    consumer: Processor<in PsiReference>,
    optimizer: SearchRequestCollector,
    options: KotlinReferencesSearchOptions,
    private val isSet: Boolean
) : OperatorReferenceSearcher<KtArrayAccessExpression>(
    targetFunction,
    searchScope,
    consumer,
    optimizer,
    options,
    wordsToSearch = listOf("[")
) {

    override fun processPossibleReceiverExpression(expression: KtExpression) {
        val accessExpression = expression.parent as? KtArrayAccessExpression ?: return
        if (expression != accessExpression.arrayExpression) return
        if (!checkAccessExpression(accessExpression)) return
        processReferenceElement(accessExpression)
    }

    override fun isReferenceToCheck(ref: PsiReference) =
        ref is KtArrayAccessReference && checkAccessExpression(ref.element)

    override fun extractReference(element: KtElement): PsiReference? {
        val accessExpression = element as? KtArrayAccessExpression ?: return null
        if (!checkAccessExpression(accessExpression)) return null
        return accessExpression.references.firstIsInstance<KtArrayAccessReference>()
    }

    private fun checkAccessExpression(accessExpression: KtArrayAccessExpression): Boolean {
        val readWriteAccess = accessExpression.readWriteAccess(useResolveForReadWrite = false)
        return if (isSet) readWriteAccess.isWrite else readWriteAccess.isRead
    }
}