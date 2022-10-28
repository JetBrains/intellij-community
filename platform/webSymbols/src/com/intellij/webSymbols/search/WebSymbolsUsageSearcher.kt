// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.webSymbols.search

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Query
import com.intellij.webSymbols.*
import com.intellij.webSymbols.declarations.WebSymbolDeclarationProvider
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import java.util.*

class WebSymbolsUsageSearcher : UsageSearcher {

  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> =
    parameters.target
      .let { it as? WebSymbol ?: (it as? WebSymbolSearchTarget)?.symbol }
      ?.let { buildWebSymbolUsagesQueries(it, parameters.project, parameters.searchScope) }
    ?: emptyList()

  companion object {

    @JvmStatic
    fun buildWebSymbolUsagesQueries(symbol: WebSymbol, project: Project, searchScope: SearchScope) =
      (symbol.psiContext
         ?.let { WebSymbolsQueryExecutorFactory.create(it, true) }
         ?.namesProvider
         ?.getNames(symbol.namespace, symbol.kind,
                    symbol.name, WebSymbolNamesProvider.Target.NAMES_QUERY)?.asSequence()
       ?: sequenceOf(symbol.name))
        .map { it.lowercase(Locale.US) }
        .distinct()
        .map {
          SearchService.getInstance()
            .searchWord(project, symbol.name)
            .caseSensitive(false)
            .inContexts(SearchContext.IN_CODE_HOSTS, SearchContext.IN_CODE, SearchContext.IN_PLAIN_TEXT, SearchContext.IN_STRINGS)
            .includeInjections()
            .inScope(searchScope)
            .buildQuery(LeafOccurrenceMapper.withPointer(symbol.createPointer(), Companion::findReferencesToSymbol))
        }
        .toList()

    private fun findReferencesToSymbol(symbol: WebSymbol, leafOccurrence: LeafOccurrence): Collection<PsiUsage> =
      service<PsiSymbolReferenceService>().run {
        for ((element, offsetInElement) in walkUp(leafOccurrence.start, leafOccurrence.offsetInStart, leafOccurrence.scope)) {
          if (element !is PsiExternalReferenceHost)
            continue

          val declarations = WebSymbolDeclarationProvider.getAllDeclarations(element, offsetInElement)
          if (declarations.isNotEmpty()) {
            return declarations
              .filter { it.symbol.isEquivalentTo(symbol) }
              .map {
                WebSymbolPsiUsage(it.declaringElement.containingFile,
                                  it.rangeInDeclaringElement.shiftRight(it.declaringElement.startOffset),
                                  true)
              }
          }

          return getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement))
            .asSequence()
            .filterIsInstance<WebSymbolReference>()
            .filter { it.rangeInElement.containsOffset(offsetInElement) }
            .filter { ref -> ref.resolvesTo(symbol) }
            .map { WebSymbolPsiUsage(it.element.containingFile, it.absoluteRange, false) }
            .toList()
        }

        emptyList()
      }
  }

}