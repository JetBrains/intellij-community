// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.usagesSearch.operators

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.references.KtForLoopInReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class IteratorOperatorReferenceSearcher(
    targetFunction: PsiElement,
    searchScope: SearchScope,
    consumer: Processor<in PsiReference>,
    optimizer: SearchRequestCollector,
    options: KotlinReferencesSearchOptions
) : OperatorReferenceSearcher<KtForExpression>(targetFunction, searchScope, consumer, optimizer, options, wordsToSearch = listOf("in")) {

    override fun processPossibleReceiverExpression(expression: KtExpression) {
        val parent = expression.parent
        if (parent.node.elementType == KtNodeTypes.LOOP_RANGE) {
            processReferenceElement(parent.parent as KtForExpression)
        }
    }

    override fun isReferenceToCheck(ref: PsiReference): Boolean {
        return ref is KtForLoopInReference
    }

    override fun extractReference(element: KtElement): PsiReference? {
        return (element as? KtForExpression)?.references?.firstIsInstance<KtForLoopInReference>()
    }
}