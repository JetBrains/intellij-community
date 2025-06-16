// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.patterns.PolySymbolsPatternSymbolsResolver
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolsScope
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.nameMatches
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.util.containers.Stack

class SingleSymbolReferencePattern(
  private val path: List<PolySymbolQualifiedName>,
  private val excludeModifiers: List<PolySymbolModifier> = listOf(PolySymbolModifier.ABSTRACT),
) : PolySymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> =
    emptySequence()

  override fun match(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolsScope>,
    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
    params: MatchParameters,
    start: Int,
    end: Int,
  ): List<MatchResult> =
    if (owner?.nameMatches(params.name.substring(start, end), params.queryExecutor) == true)
      params.queryExecutor.nameMatchQuery(path) {
        exclude(excludeModifiers)
        additionalScope(scopeStack)
      }
        .asSingleSymbol()
        ?.let { listOf(MatchResult(PolySymbolNameSegment.create(start, end, it))) }
      ?: emptyList()
    else
      emptyList()

  override fun list(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolsScope>,
    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult> =
    if (owner != null) {
      params.queryExecutor.nameMatchQuery(path) {
        exclude(excludeModifiers)
        additionalScope(scopeStack)
      }
        .asSingleSymbol()
        ?.let { listOf(ListResult(owner.name, PolySymbolNameSegment.create(0, owner.name.length, it))) }
      ?: emptyList()
    }
    else emptyList()

  override fun complete(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolsScope>,
    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
    params: CompletionParameters,
    start: Int,
    end: Int,
  ): CompletionResults =
    if (owner != null
        && params.queryExecutor.nameMatchQuery(path) {
        exclude(excludeModifiers)
        additionalScope(scopeStack)
      }.isNotEmpty()) {
      CompletionResults(params.queryExecutor.namesProvider
                          .getNames(owner.qualifiedName, PolySymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
                          .map { PolySymbolCodeCompletionItem.create(it, 0, symbol = owner) })
    }
    else {
      CompletionResults(emptyList())
    }
}