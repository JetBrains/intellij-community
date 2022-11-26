package com.intellij.webSymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.ReferenceRange
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.util.Processor
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.references.WebSymbolReference

class PsiSourcedWebSymbolRequestResultProcessor(private val targetElement: PsiElement,
                                                private val includeRegularReferences: Boolean) : RequestResultProcessor() {
  private val mySymbolReferenceService = service<PsiSymbolReferenceService>()
  private val myPsiReferenceService = PsiReferenceService.getService()

  override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
    if (!targetElement.isValid) {
      return false
    }
    if (element is PsiExternalReferenceHost) {
      // Web symbol references
      mySymbolReferenceService.getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement))
        .asSequence()
        .filterIsInstance<WebSymbolReference>()
        .filter { it.rangeInElement.containsOffset(offsetInElement) }
        .forEach { ref ->
          ProgressManager.checkCanceled()
          val psiSourcedWebSymbols = ref.resolveReference().filterIsInstance<PsiSourcedWebSymbol>()
          if (psiSourcedWebSymbols.isEmpty()) return@forEach
          val targetSymbol = PsiSymbolService.getInstance().asSymbol(targetElement)
          val equivalentSymbol = psiSourcedWebSymbols.find { it.isEquivalentTo(targetSymbol) } ?: return@forEach
          if (!consumer.process(
              PsiSourcedWebSymbolReference(equivalentSymbol, targetElement, element, ref.rangeInElement))) {
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