// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.impl.canUnwrapSymbols
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.webTypes.filters.PolySymbolFilter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolPatternReferenceResolver(private vararg val items: Reference) : PolySymbolPatternSymbolsResolver {
  override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
    items.asSequence().map { it.qualifiedKind }.toSet()

  override val delegate: PolySymbol?
    get() = null

  override fun codeCompletion(
    name: String,
    position: Int,
    stack: PolySymbolQueryStack,
    queryExecutor: PolySymbolQueryExecutor,
  ): List<PolySymbolCodeCompletionItem> =
    items.flatMap { it.codeCompletion(name, stack, queryExecutor, position) }

  override fun matchName(name: String, stack: PolySymbolQueryStack, queryExecutor: PolySymbolQueryExecutor): List<PolySymbol> =
    items.asSequence()
      .flatMap { it.resolve(name, stack, queryExecutor) }
      .flatMap {
        if (it is PolySymbolMatch
            && it.nameSegments.size == 1
            && it.nameSegments[0].canUnwrapSymbols())
          it.nameSegments[0].symbols
        else listOf(it)
      }
      .toList()

  override fun listSymbols(
    stack: PolySymbolQueryStack,
    queryExecutor: PolySymbolQueryExecutor,
    expandPatterns: Boolean,
  ): List<PolySymbol> =
    items.flatMap { it.listSymbols(stack, queryExecutor, expandPatterns) }

  data class Reference(
    val location: List<PolySymbolQualifiedName> = emptyList(),
    val qualifiedKind: PolySymbolQualifiedKind,
    val filter: PolySymbolFilter? = null,
    val excludeModifiers: List<PolySymbolModifier> = listOf(PolySymbolModifier.Companion.ABSTRACT),
    val nameConversionRules: List<PolySymbolNameConversionRules> = emptyList(),
  ) {
    fun resolve(
      name: String,
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
    ): List<PolySymbol> {
      val matches = queryExecutor.withNameConversionRules(nameConversionRules)
        .nameMatchQuery(location + qualifiedKind.withName(name)) {
          exclude(excludeModifiers)
          additionalScope(stack)
        }
      if (filter == null) return matches
      return filter.filterNameMatches(matches, queryExecutor, stack, emptyMap())
    }

    fun listSymbols(
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
      expandPatterns: Boolean,
    ): List<PolySymbol> {
      val symbols = queryExecutor.withNameConversionRules(nameConversionRules)
        .listSymbolsQuery(location, qualifiedKind, expandPatterns) {
          exclude(excludeModifiers)
          additionalScope(stack)
        }
      if (filter == null) return symbols
      return filter.filterNameMatches(symbols, queryExecutor, stack, emptyMap())
    }

    fun codeCompletion(
      name: String,
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
      position: Int,
    ): List<PolySymbolCodeCompletionItem> {
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .codeCompletionQuery(location + qualifiedKind.withName(name), position) {
          exclude(excludeModifiers)
          additionalScope(stack)
        }
      if (filter == null) return codeCompletions
      return filter.filterCodeCompletions(codeCompletions, queryExecutor, stack, emptyMap())
    }

  }

}