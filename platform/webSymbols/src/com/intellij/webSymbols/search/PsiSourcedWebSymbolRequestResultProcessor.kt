package com.intellij.webSymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.ReferenceRange
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.util.Processor
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.utils.asSingleSymbol
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PsiSourcedWebSymbolRequestResultProcessor(private val targetElement: PsiElement,
                                                private val targetSymbols: List<WebSymbol>,
                                                private val includeRegularReferences: Boolean) : RequestResultProcessor() {
  private val mySymbolReferenceService = PsiSymbolReferenceService.getService()
  private val myPsiReferenceService = PsiReferenceService.getService()
  private val myTargetSymbol = PsiSymbolService.getInstance().asSymbol(targetElement)

  override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
    if (!targetElement.isValid) {
      return false
    }
    if (element is PsiExternalReferenceHost) {
      val targetSymbol = targetSymbols.asSingleSymbol() ?: myTargetSymbol
      // Web symbol references
      mySymbolReferenceService.getReferences(element, WebSymbolReferenceHints(targetSymbol, offsetInElement))
        .asSequence()
        .filterIsInstance<WebSymbolReference>()
        .filter { it.rangeInElement.containsOffset(offsetInElement) }
        .forEach { ref ->
          ProgressManager.checkCanceled()
          val psiSourcedWebSymbols = ref.resolveReference().filterIsInstance<PsiSourcedWebSymbol>()
          if (psiSourcedWebSymbols.isEmpty()) return@forEach
          val equivalentSymbol = if (targetSymbols.isEmpty()) {
            psiSourcedWebSymbols.find { it.isEquivalentTo(myTargetSymbol) }
          }
          else {
            targetSymbols.find { targetSymbol -> psiSourcedWebSymbols.any { it.isEquivalentTo(targetSymbol) } }
          }
          if (equivalentSymbol == null) return@forEach
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