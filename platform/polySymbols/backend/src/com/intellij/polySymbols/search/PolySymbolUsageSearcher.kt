// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

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
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.startOffset
import com.intellij.psi.util.walkUp
import com.intellij.util.Query
import java.util.*

internal class PolySymbolUsageSearcher : UsageSearcher {

  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> =
    parameters.target
      .let { it as? PolySymbol ?: (it as? PolySymbolSearchTarget)?.symbol }
      ?.let { PolySymbolUsageQueries.buildPolySymbolUsagesQueries(it, parameters.project, parameters.searchScope) }
    ?: emptyList()

}

object PolySymbolUsageQueries {

  fun buildPolySymbolUsagesQueries(symbol: PolySymbol, project: Project, searchScope: SearchScope): List<Query<out PsiUsage>> =
    (symbol.psiContext
       ?.let { PolySymbolQueryExecutorFactory.create(it, true) }
       ?.namesProvider
       ?.getNames(symbol.qualifiedName, PolySymbolNamesProvider.Target.NAMES_QUERY)?.asSequence()
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

  private fun findReferencesToSymbol(symbol: PolySymbol, leafOccurrence: LeafOccurrence): Collection<PsiUsage> =
    service<PsiSymbolReferenceService>().run {
      for ((element, offsetInElement) in walkUp(leafOccurrence.start, leafOccurrence.offsetInStart, leafOccurrence.scope)) {
        val psiSource = (symbol as? PsiSourcedPolySymbol)?.source

        if (psiSource == element) {
          val nameIdentifier = (element as? PsiNameIdentifierOwner)?.nameIdentifier
          if (nameIdentifier != null)
            return listOf(PolySymbolPsiUsage(element.containingFile, nameIdentifier.textRange, true))
        }

        if (element is PsiExternalReferenceHost) {
          val declarations = PolySymbolDeclarationProvider.getAllEquivalentDeclarations(element, offsetInElement, symbol)
          if (declarations.isNotEmpty()) {
            return declarations
              .map {
                PolySymbolPsiUsage(it.declaringElement.containingFile,
                                   it.rangeInDeclaringElement.shiftRight(it.declaringElement.startOffset),
                                   true)
              }
          }

          val foundReferences = getReferences(element, PolySymbolReferenceHints(symbol, offsetInElement))
            .asSequence()
            .filterIsInstance<PolySymbolReference>()
            .filter { it.rangeInElement.containsOffset(offsetInElement) }
            .filter { ref -> ref.resolvesTo(symbol) }
            .map { PolySymbolPsiUsage(it.element.containingFile, it.absoluteRange, false) }
            .toList()

          if (foundReferences.isNotEmpty()) {
            return foundReferences
          }
        }

        if (psiSource != null) {
          val foundReferences = element.references.asSequence()
            .filter { it.rangeInElement.containsOffset(offsetInElement) }
            .filter { it.isReferenceTo(psiSource) }
            .map { PolySymbolPsiUsage(it.element.containingFile, it.absoluteRange, false) }
            .toList()

          if (foundReferences.isNotEmpty()) {
            return foundReferences
          }
        }
      }
      emptyList()
    }
}

internal class PolySymbolReferenceHints(private val symbol: Symbol? = null, private val offsetInElement: Int = -1) : PsiSymbolReferenceHints {
  override fun getOffsetInElement(): Int {
    return offsetInElement
  }

  override fun getReferenceClass(): Class<out PsiSymbolReference> {
    return PolySymbolReference::class.java
  }

  override fun getTarget(): Symbol? {
    return symbol
  }

  companion object {
    val NO_HINTS: PsiSymbolReferenceHints = object : PsiSymbolReferenceHints {}
  }
}
