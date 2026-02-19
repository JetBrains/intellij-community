// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver
import com.intellij.polySymbols.query.PolySymbolQueryStack

internal class StaticPattern(val content: String) : PolySymbolPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(content)

  override fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters,
    start: Int,
    end: Int,
  ): List<MatchResult> =
    if (content.length <= end - start && params.name.startsWith(content, start))
      listOf(MatchResult(PolySymbolNameSegment.create(start, start + content.length)))
    else emptyList()

  override fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult> =
    listOf(ListResult(content, PolySymbolNameSegment.create(0, content.length)))

  override fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters,
    start: Int,
    end: Int,
  ): CompletionResults =
    CompletionResults(PolySymbolCodeCompletionItem.create(content, start))

  override fun toString(): String =
    "\"${content}\""
}