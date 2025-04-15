// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.startOffset
import com.intellij.psi.util.walkUp
import com.intellij.util.Query
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.declarations.WebSymbolDeclarationProvider
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.utils.qualifiedName
import java.util.*

internal class WebSymbolsUsageSearcher : UsageSearcher {

  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> =
    parameters.target
      .let { it as? WebSymbol ?: (it as? WebSymbolSearchTarget)?.symbol }
      ?.let { WebSymbolUsageQueries.buildWebSymbolUsagesQueries(it, parameters.project, parameters.searchScope) }
    ?: emptyList()

}

object WebSymbolUsageQueries {

  fun buildWebSymbolUsagesQueries(symbol: WebSymbol, project: Project, searchScope: SearchScope): List<Query<out PsiUsage>> =
    (symbol.psiContext
       ?.let { WebSymbolsQueryExecutorFactory.create(it, true) }
       ?.namesProvider
       ?.getNames(symbol.qualifiedName, WebSymbolNamesProvider.Target.NAMES_QUERY)?.asSequence()
     ?: sequenceOf(symbol.name))
      .map { it.lowercase(Locale.US) }
      .distinct()
      .map {
        SearchService.getInstance()
          .searchWord(project, it)
          .caseSensitive(false)
          .inContexts(SearchContext.inCodeHosts(), SearchContext.inCode(), SearchContext.inPlainText(), SearchContext.inStrings())
          .includeInjections()
          .inScope(searchScope)
          .buildQuery(LeafOccurrenceMapper.withPointer(symbol.createPointer(), ::findReferencesToSymbol))
      }
      .toList()

  private fun findReferencesToSymbol(symbol: WebSymbol, leafOccurrence: LeafOccurrence): Collection<PsiUsage> =
    service<PsiSymbolReferenceService>().run {
      for ((element, offsetInElement) in walkUp(leafOccurrence.start, leafOccurrence.offsetInStart, leafOccurrence.scope)) {
        val psiSource = (symbol as? PsiSourcedWebSymbol)?.source

        if (psiSource == element) {
          val nameIdentifier = (element as? PsiNameIdentifierOwner)?.nameIdentifier
          if (nameIdentifier != null)
            return listOf(WebSymbolPsiUsage(element.containingFile, nameIdentifier.textRange, true))
        }

        if (element is PsiExternalReferenceHost) {
          val declarations = WebSymbolDeclarationProvider.getAllEquivalentDeclarations(element, offsetInElement, symbol)
          if (declarations.isNotEmpty()) {
            return declarations
              .map {
                WebSymbolPsiUsage(it.declaringElement.containingFile,
                                  it.rangeInDeclaringElement.shiftRight(it.declaringElement.startOffset),
                                  true)
              }
          }

          val foundReferences = getReferences(element, WebSymbolReferenceHints(symbol, offsetInElement))
            .asSequence()
            .filterIsInstance<WebSymbolReference>()
            .filter { it.rangeInElement.containsOffset(offsetInElement) }
            .filter { ref -> ref.resolvesTo(symbol) }
            .map { WebSymbolPsiUsage(it.element.containingFile, it.absoluteRange, false) }
            .toList()

          if (foundReferences.isNotEmpty()) {
            return foundReferences
          }
        }

        if (psiSource != null) {
          val foundReferences = element.references.asSequence()
            .filter { it.rangeInElement.containsOffset(offsetInElement) }
            .filter { it.isReferenceTo(psiSource) }
            .map { WebSymbolPsiUsage(it.element.containingFile, it.absoluteRange, false) }
            .toList()

          if (foundReferences.isNotEmpty()) {
            return foundReferences
          }
        }
      }
      emptyList()
    }
}

internal class WebSymbolReferenceHints(private val symbol: Symbol, private val offsetInElement: Int) : PsiSymbolReferenceHints {
  override fun getOffsetInElement(): Int {
    return offsetInElement
  }

  override fun getReferenceClass(): Class<out PsiSymbolReference> {
    return WebSymbolReference::class.java
  }

  override fun getTarget(): Symbol {
    return symbol
  }

  companion object {
    val NO_HINTS: PsiSymbolReferenceHints = object : PsiSymbolReferenceHints {}
  }
}
