// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.usagesSearch.operators

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.references.KtPropertyDelegationMethodsReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class PropertyDelegationOperatorReferenceSearcher(
    targetFunction: PsiElement,
    searchScope: SearchScope,
    consumer: Processor<in PsiReference>,
    optimizer: SearchRequestCollector,
    options: KotlinReferencesSearchOptions
) : OperatorReferenceSearcher<KtPropertyDelegate>(targetFunction, searchScope, consumer, optimizer, options, wordsToSearch = listOf("by")) {

    override fun processPossibleReceiverExpression(expression: KtExpression) {
        (expression.parent as? KtPropertyDelegate)?.let { processReferenceElement(it) }
    }

    override fun isReferenceToCheck(ref: PsiReference): Boolean {
        return ref is KtPropertyDelegationMethodsReference
    }

    override fun extractReference(element: KtElement): PsiReference? {
        return (element as? KtPropertyDelegate)?.references?.firstIsInstance<KtPropertyDelegationMethodsReference>()
    }
}