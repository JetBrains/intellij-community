// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.webTypes.filters.WebSymbolsFilter

class WebSymbolsPatternReferenceResolver(private vararg val items: Reference) : WebSymbolsPatternSymbolsResolver {
  override fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
    items.asSequence().map { it.qualifiedKind }.toSet()

  override val delegate: WebSymbol?
    get() = null

  override fun codeCompletion(name: String,
                              position: Int,
                              scopeStack: Stack<WebSymbolsScope>,
                              queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolCodeCompletionItem> =
    items.flatMap { it.codeCompletion(name, scopeStack, queryExecutor, position) }

  override fun matchName(name: String, scopeStack: Stack<WebSymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<WebSymbol> =
    items.asSequence()
      .flatMap { it.resolve(name, scopeStack, queryExecutor) }
      .flatMap {
        if (it is WebSymbolMatch
            && it.nameSegments.size == 1
            && it.nameSegments[0].canUnwrapSymbols())
          it.nameSegments[0].symbols
        else listOf(it)
      }
      .toList()

  override fun listSymbols(scopeStack: Stack<WebSymbolsScope>,
                           queryExecutor: WebSymbolsQueryExecutor,
                           expandPatterns: Boolean): List<WebSymbol> =
    items.flatMap { it.listSymbols(scopeStack, queryExecutor, expandPatterns) }

  data class Reference(
    val location: List<WebSymbolQualifiedName> = emptyList(),
    val qualifiedKind: WebSymbolQualifiedKind,
    val includeVirtual: Boolean = true,
    val includeAbstract: Boolean = false,
    val filter: WebSymbolsFilter? = null,
    val nameConversionRules: List<WebSymbolNameConversionRules> = emptyList(),
  ) {
    fun resolve(name: String,
                scope: Stack<WebSymbolsScope>,
                queryExecutor: WebSymbolsQueryExecutor
    ): List<WebSymbol> {
      val matches = queryExecutor.withNameConversionRules(nameConversionRules)
        .runNameMatchQuery(location + WebSymbolQualifiedName(qualifiedKind.namespace, qualifiedKind.kind, name),
                           includeVirtual, includeAbstract, false, scope)
      if (filter == null) return matches
      return filter.filterNameMatches(matches, queryExecutor, scope, emptyMap())
    }

    fun listSymbols(scope: Stack<WebSymbolsScope>,
                    queryExecutor: WebSymbolsQueryExecutor,
                    expandPatterns: Boolean): List<WebSymbol> {
      val symbols = queryExecutor.withNameConversionRules(nameConversionRules)
        .runListSymbolsQuery(location, qualifiedKind,
                             expandPatterns, includeVirtual, includeAbstract, false, scope)
      if (filter == null) return symbols
      return filter.filterNameMatches(symbols, queryExecutor, scope, emptyMap())
    }

    fun codeCompletion(name: String,
                       scopeStack: Stack<WebSymbolsScope>,
                       queryExecutor: WebSymbolsQueryExecutor,
                       position: Int): List<WebSymbolCodeCompletionItem> {
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .runCodeCompletionQuery(location + WebSymbolQualifiedName(qualifiedKind.namespace, qualifiedKind.kind, name),
                                position, includeVirtual, scopeStack)
      if (filter == null) return codeCompletions
      return filter.filterCodeCompletions(codeCompletions, queryExecutor, scopeStack, emptyMap())
    }

  }

}