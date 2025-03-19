// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.ReferenceRange
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.KtDestructuringDeclarationReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isCallableOverrideUsage
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isExtensionOfDeclarationClassUsage
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInvokeOfCompanionObject
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isUsageInContainingDeclaration
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isUsageOfActual
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinRequestResultProcessor(
    private val unwrappedElement: PsiElement,
    private val originalElement: PsiElement = unwrappedElement,
    private val filter: (PsiReference) -> Boolean = { true },
    private val options: KotlinReferencesSearchOptions = KotlinReferencesSearchOptions.Empty
) : RequestResultProcessor(unwrappedElement, originalElement, filter, options) {
    private val referenceService = PsiReferenceService.getService()

    override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
        val references = if (element is KtDestructuringDeclaration)
            element.entries.flatMap { referenceService.getReferences(it, PsiReferenceService.Hints.NO_HINTS) }
        else
            referenceService.getReferences(element, PsiReferenceService.Hints.NO_HINTS)
        return references.all { ref ->
            ProgressManager.checkCanceled()

            if (filter(ref) && ref.containsOffsetInElement(offsetInElement) && ref.isReferenceToTarget(unwrappedElement)) {
                consumer.process(ref)
            } else {
                true
            }
        }
    }

    private fun PsiReference.containsOffsetInElement(offsetInElement: Int): Boolean {
        if (this is KtDestructuringDeclarationReference) return true
        return ReferenceRange.containsOffsetInElement(this, offsetInElement)
    }

    private fun PsiReference.isReferenceToTarget(element: PsiElement): Boolean {
        if (isReferenceTo(element)) {
            return true
        }
        if (resolve()?.unwrapped == element.originalElement) {
            return true
        }

        if (originalElement is KtNamedDeclaration) {
            if (options.searchForExpectedUsages && isUsageOfActual(originalElement)
            ) {
                return true
            }

            if (isInvokeOfCompanionObject(originalElement)) {
                return true
            }
            if (options.acceptCallableOverrides && isCallableOverrideUsage(originalElement)) {
                return true
            }
            if (options.acceptOverloads && originalElement is KtFunction && isUsageInContainingDeclaration(originalElement)) {
                return true
            }
            if (options.acceptExtensionsOfDeclarationClass && isExtensionOfDeclarationClassUsage(originalElement)) {
                return true
            }
        }
        return false
    }
}