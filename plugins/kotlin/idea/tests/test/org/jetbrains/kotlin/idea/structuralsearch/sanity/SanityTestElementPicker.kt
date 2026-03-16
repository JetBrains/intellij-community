// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.sanity

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDeclarationModifierList
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenEntry
import kotlin.random.Random

object SanityTestElementPicker {

    private val random = Random(System.currentTimeMillis())
    private const val KEEP_RATIO = 0.7

    /** Tells which Kotlin [PsiElement]-s can be used directly or not (i.e. some child) as a SSR pattern. */
    private val PsiElement.isSearchable: Boolean
        get() = when (this) {
            is PsiWhiteSpace, is KtPackageDirective, is KtImportList -> false
            is KtParameterList, is KtValueArgumentList, is KtSuperTypeList, is KtTypeArgumentList, is KtTypeParameterList,
            is KtBlockExpression, is KtClassBody -> this.children.any()
            is KtModifierList, is KtDeclarationModifierList -> false
            is LeafPsiElement, is KtOperationReferenceExpression -> false
            is KtLiteralStringTemplateEntry, is KtEscapeStringTemplateEntry -> false
            is KDocSection -> false
            else -> true
        }

    private fun shouldStopAt(element: PsiElement) = when (element) {
        is KtAnnotationEntry -> true
        else -> false
    }

    private fun mustContinueAfter(element: PsiElement) = when (element) {
        is KtParameterList, is KtValueArgumentList, is KtSuperTypeList, is KtTypeArgumentList, is KtTypeParameterList -> true
        is KtClassBody, is KtBlockExpression, is KtBlockCodeFragment -> true
        is KtPrimaryConstructor -> true
        is KtParameter -> true
        is KtSimpleNameStringTemplateEntry, is KtBlockStringTemplateEntry, is KtSuperTypeCallEntry -> true
        is KtClassOrObject -> (element.body?.children?.size ?: 0) > 4
        is KtContainerNode -> true
        is KtWhenEntry -> true
        is KtPropertyAccessor -> true
        else -> false
    }

    /** Returns a random [PsiElement] whose text can be used as a pattern against [tree]. */
    fun pickFrom(tree: Array<PsiElement>): PsiElement? {
        if (tree.isEmpty()) return null
        var element = tree.filter { it.isSearchable }.random()

        var canContinue: Boolean
        var mustContinue: Boolean
        do {
            val searchableChildren = element.children.filter { it.isSearchable }
            if (searchableChildren.isEmpty()) break

            element = searchableChildren.random()

            canContinue = element.children.any { it.isSearchable } && !shouldStopAt(element)
            mustContinue = random.nextFloat() > KEEP_RATIO || mustContinueAfter(element)
        } while (canContinue && mustContinue)

        return element
    }

}