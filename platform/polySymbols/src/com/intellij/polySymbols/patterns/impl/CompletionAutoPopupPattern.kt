// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.patterns.PolySymbolsPatternSymbolsResolver
import com.intellij.polySymbols.utils.hideFromCompletion

internal class CompletionAutoPopupPattern(val isSticky: Boolean) : PolySymbolsPattern() {

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun match(owner: PolySymbol?,
                     scopeStack: Stack<PolySymbolsScope>,
                     symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    listOf(MatchResult(PolySymbolNameSegment.create(start, start)))

  override fun list(owner: PolySymbol?,
                    scopeStack: Stack<PolySymbolsScope>,
                    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                    params: ListParameters): List<ListResult> =
    emptyList()

  override fun complete(owner: PolySymbol?,
                        scopeStack: Stack<PolySymbolsScope>,
                        symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                        params: CompletionParameters,
                        start: Int,
                        end: Int): CompletionResults =
    if (symbolsResolver == null || symbolsResolver.delegate?.hideFromCompletion == true) {
      CompletionResults(emptyList(), true)
    }
    else {
      CompletionResults(PolySymbolCodeCompletionItem.create("", start, true, displayName = "…"))
    }

  override fun toString(): String {
    return "…" + (if (isSticky) "$" else "")
  }
}