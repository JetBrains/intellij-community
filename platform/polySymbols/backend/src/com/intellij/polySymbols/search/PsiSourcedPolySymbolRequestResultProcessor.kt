// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.ReferenceRange
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PsiSourcedPolySymbolRequestResultProcessor(
  private val targetElement: PsiElement,
  private val targetSymbols: List<PolySymbol>,
  private val includeRegularReferences: Boolean,
) : RequestResultProcessor() {
  private val mySymbolReferenceService = PsiSymbolReferenceService.getService()
  private val myPsiReferenceService = PsiReferenceService.getService()
  private val myTargetSymbol = PsiSymbolService.getInstance().asSymbol(targetElement)

  override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
    if (!targetElement.isValid) {
      return false
    }
    if (element is PsiExternalReferenceHost) {
      val targetSymbol = targetSymbols.asSingleSymbol() ?: myTargetSymbol
      // Poly symbol references
      mySymbolReferenceService.getReferences(element, PolySymbolReferenceHints(targetSymbol, offsetInElement))
        .asSequence()
        .filterIsInstance<PolySymbolReference>()
        .filter { it.rangeInElement.containsOffset(offsetInElement) }
        .forEach { ref ->
          ProgressManager.checkCanceled()
          val psiSourcedPolySymbols = ref.resolveReference().filterIsInstance<PsiSourcedPolySymbol>()
          if (psiSourcedPolySymbols.isEmpty()) return@forEach
          val equivalentSymbol = if (targetSymbols.isEmpty()) {
            psiSourcedPolySymbols.find { it.isEquivalentTo(myTargetSymbol) }
          }
          else {
            targetSymbols.find { targetSymbol -> psiSourcedPolySymbols.any { it.isEquivalentTo(targetSymbol) } }
          }
          if (equivalentSymbol == null) return@forEach
          if (!consumer.process(
              PsiSourcedPolySymbolReference(equivalentSymbol, targetElement, element, ref.rangeInElement))) {
            return false
          }
        }
    }

    if (includeRegularReferences) {
      // Regular psi references
      val references = myPsiReferenceService.getReferences(element,
                                                           PsiReferenceService.Hints(targetElement, offsetInElement))
      //noinspection ForLoopReplaceableByForEach
      for (i in references.indices) {
        val ref = references[i]
        ProgressManager.checkCanceled()
        if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)
            && ref.isReferenceTo(targetElement) && !consumer.process(ref)) {
          return false
        }
      }
    }
    return true
  }

}