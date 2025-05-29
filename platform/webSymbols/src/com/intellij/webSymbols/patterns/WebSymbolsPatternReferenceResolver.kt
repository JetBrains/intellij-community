// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.PolySymbolQualifiedName
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.impl.canUnwrapSymbols
import com.intellij.webSymbols.query.PolySymbolMatch
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.webTypes.filters.WebSymbolsFilter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WebSymbolsPatternReferenceResolver(private vararg val items: Reference) : WebSymbolsPatternSymbolsResolver {
  override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
    items.asSequence().map { it.qualifiedKind }.toSet()

  override val delegate: PolySymbol?
    get() = null

  override fun codeCompletion(name: String,
                              position: Int,
                              scopeStack: Stack<PolySymbolsScope>,
                              queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolCodeCompletionItem> =
    items.flatMap { it.codeCompletion(name, scopeStack, queryExecutor, position) }

  override fun matchName(name: String, scopeStack: Stack<PolySymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<PolySymbol> =
    items.asSequence()
      .flatMap { it.resolve(name, scopeStack, queryExecutor) }
      .flatMap {
        if (it is PolySymbolMatch
            && it.nameSegments.size == 1
            && it.nameSegments[0].canUnwrapSymbols())
          it.nameSegments[0].symbols
        else listOf(it)
      }
      .toList()

  override fun listSymbols(scopeStack: Stack<PolySymbolsScope>,
                           queryExecutor: WebSymbolsQueryExecutor,
                           expandPatterns: Boolean): List<PolySymbol> =
    items.flatMap { it.listSymbols(scopeStack, queryExecutor, expandPatterns) }

  data class Reference(
    val location: List<PolySymbolQualifiedName> = emptyList(),
    val qualifiedKind: PolySymbolQualifiedKind,
    val includeVirtual: Boolean = true,
    val includeAbstract: Boolean = false,
    val filter: WebSymbolsFilter? = null,
    val nameConversionRules: List<WebSymbolNameConversionRules> = emptyList(),
  ) {
    fun resolve(name: String,
                scope: Stack<PolySymbolsScope>,
                queryExecutor: WebSymbolsQueryExecutor
    ): List<PolySymbol> {
      val matches = queryExecutor.withNameConversionRules(nameConversionRules)
        .runNameMatchQuery(location + PolySymbolQualifiedName(qualifiedKind.namespace, qualifiedKind.kind, name),
                           includeVirtual, includeAbstract, false, scope)
      if (filter == null) return matches
      return filter.filterNameMatches(matches, queryExecutor, scope, emptyMap())
    }

    fun listSymbols(scope: Stack<PolySymbolsScope>,
                    queryExecutor: WebSymbolsQueryExecutor,
                    expandPatterns: Boolean): List<PolySymbol> {
      val symbols = queryExecutor.withNameConversionRules(nameConversionRules)
        .runListSymbolsQuery(location, qualifiedKind,
                             expandPatterns, includeVirtual, includeAbstract, false, scope)
      if (filter == null) return symbols
      return filter.filterNameMatches(symbols, queryExecutor, scope, emptyMap())
    }

    fun codeCompletion(name: String,
                       scopeStack: Stack<PolySymbolsScope>,
                       queryExecutor: WebSymbolsQueryExecutor,
                       position: Int): List<WebSymbolCodeCompletionItem> {
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .runCodeCompletionQuery(location + PolySymbolQualifiedName(qualifiedKind.namespace, qualifiedKind.kind, name),
                                position, includeVirtual, scopeStack)
      if (filter == null) return codeCompletions
      return filter.filterCodeCompletions(codeCompletions, queryExecutor, scopeStack, emptyMap())
    }

  }

}