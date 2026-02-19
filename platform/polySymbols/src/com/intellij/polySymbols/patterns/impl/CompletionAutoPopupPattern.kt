// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.utils.hideFromCompletion

internal class CompletionAutoPopupPattern(val isSticky: Boolean) : PolySymbolPattern() {

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters,
    start: Int,
    end: Int,
  ): List<MatchResult> =
    listOf(MatchResult(PolySymbolNameSegment.create(start, start)))

  override fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult> =
    emptyList()

  override fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters,
    start: Int,
    end: Int,
  ): CompletionResults =
    if (symbolsResolver == null || symbolsResolver.delegate?.hideFromCompletion == true) {
      CompletionResults(emptyList(), true)
    }
    else {
      CompletionResults(
        PolySymbolCodeCompletionItem
          .builder("", start)
          .completeAfterInsert(true)
          .displayName("…")
          .build()
      )
    }

  override fun toString(): String {
    return "…" + (if (isSticky) "$" else "")
  }
}