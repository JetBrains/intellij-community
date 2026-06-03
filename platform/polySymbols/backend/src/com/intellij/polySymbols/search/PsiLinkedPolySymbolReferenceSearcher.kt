// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.search.impl.PolySymbolPsiLinkedSymbolHostClassEP
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

internal class PsiLinkedPolySymbolReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val targetElement = queryParameters.elementToSearch
    if (elementClasses.value.none { it.isInstance(targetElement) }) return

    val foundSymbols = PsiLinkedPolySymbolProvider.getAllSymbols(queryParameters.elementToSearch)
      .filter { it.linkedElement == targetElement }
    val names = if (foundSymbols.isNotEmpty()) {
      val queryExecutor = PolySymbolQueryExecutorFactory.create(targetElement, true)
      val namesProvider = queryExecutor.namesProvider
      foundSymbols
        .flatMap { namesProvider.getNames(it.qualifiedName, PolySymbolNamesProvider.Target.NAMES_QUERY) }
        .distinct()
    }
    else {
      (targetElement as? PsiNamedElement)?.name?.let { listOf(it) } ?: emptyList()
    }
    names.forEach {
      queryParameters.optimizer.searchWord(
        it,
        queryParameters.effectiveSearchScope,
        (UsageSearchContext.IN_CODE + UsageSearchContext.IN_FOREIGN_LANGUAGES + UsageSearchContext.IN_STRINGS).toShort(),
        false, targetElement,
        PsiLinkedPolySymbolRequestResultProcessor(targetElement, foundSymbols, false))
    }
  }

}

private val elementClasses: ClearableLazyValue<Set<Class<PsiElement>>> = ExtensionPointUtil.dropLazyValueOnChange(
  ClearableLazyValue.create {
    PolySymbolPsiLinkedSymbolHostClassEP.EP_NAME.extensionList.map { it.instance }.toSet()
  }, PolySymbolPsiLinkedSymbolHostClassEP.EP_NAME, null
)

internal fun checkPsiLinkedPolySymbolHostClasses(symbol: PolySymbol) {
  if (symbol !is PsiLinkedPolySymbol) return
  val linkedElement = symbol.linkedElement ?: return
  if (elementClasses.value.none { it.isInstance(linkedElement) }) {
    logger<PsiLinkedPolySymbol>().error("Linked element class ${linkedElement::class} or any of it's super classes are not " +
                                        "registered as a PsiLinkedPolySymbol hosts through extension point " +
                                        "`com.intellij.polySymbols.psiLinkedSymbol`.\nError on symbol ${symbol} [${symbol.javaClass}]")
  }
}