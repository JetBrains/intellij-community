// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.webSymbols.PsiSourcedWebSymbolProvider
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.search.impl.WebSymbolPsiSourcedSymbolHostClassEP
import com.intellij.webSymbols.utils.qualifiedName

internal class PsiSourcedWebSymbolReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val targetElement = queryParameters.elementToSearch
    if (elementClasses.value.none { it.isInstance(targetElement) }) return

    val foundSymbols = PsiSourcedWebSymbolProvider.getAllWebSymbols(queryParameters.elementToSearch)
      .filter { it.source == targetElement }
    val names = if (foundSymbols.isNotEmpty()) {
      val queryExecutor = WebSymbolsQueryExecutorFactory.create(targetElement, true)
      val namesProvider = queryExecutor.namesProvider
      foundSymbols
        .flatMap { namesProvider.getNames(it.qualifiedName, WebSymbolNamesProvider.Target.NAMES_QUERY) }
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
        PsiSourcedWebSymbolRequestResultProcessor(targetElement, false))
    }
  }

  private val elementClasses: ClearableLazyValue<Set<Class<PsiElement>>> = ExtensionPointUtil.dropLazyValueOnChange(
    ClearableLazyValue.create {
      WebSymbolPsiSourcedSymbolHostClassEP.EP_NAME.extensionList.map { it.instance }.toSet()
    }, WebSymbolPsiSourcedSymbolHostClassEP.EP_NAME, null
  )

}