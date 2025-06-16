// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.search.impl.PolySymbolPsiSourcedSymbolHostClassEP
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

internal class PsiSourcedPolySymbolReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val targetElement = queryParameters.elementToSearch
    if (elementClasses.value.none { it.isInstance(targetElement) }) return

    val foundSymbols = PsiSourcedPolySymbolProvider.getAllSymbols(queryParameters.elementToSearch)
      .filter { it.source == targetElement }
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
        PsiSourcedPolySymbolRequestResultProcessor(targetElement, foundSymbols, false))
    }
  }

  private val elementClasses: ClearableLazyValue<Set<Class<PsiElement>>> = ExtensionPointUtil.dropLazyValueOnChange(
    ClearableLazyValue.create {
      PolySymbolPsiSourcedSymbolHostClassEP.EP_NAME.extensionList.map { it.instance }.toSet()
    }, PolySymbolPsiSourcedSymbolHostClassEP.EP_NAME, null
  )

}