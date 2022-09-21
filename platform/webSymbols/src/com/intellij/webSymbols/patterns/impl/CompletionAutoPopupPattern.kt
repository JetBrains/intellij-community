// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider
import com.intellij.webSymbols.utils.hideFromCompletion

internal class CompletionAutoPopupPattern(val isSticky: Boolean) : WebSymbolsPattern() {

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun match(owner: WebSymbol?,
                     contextStack: Stack<WebSymbolsContainer>,
                     itemsProvider: WebSymbolsPatternItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    listOf(MatchResult(WebSymbol.NameSegment(start, start)))

  override fun getCompletionResults(owner: WebSymbol?,
                                    contextStack: Stack<WebSymbolsContainer>,
                                    itemsProvider: WebSymbolsPatternItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    if (itemsProvider == null || itemsProvider.delegate?.hideFromCompletion == true) {
      CompletionResults(emptyList(), true)
    }
    else {
      CompletionResults(WebSymbolCodeCompletionItem.create("", start, true, displayName = "…"))
    }

  override fun toString(): String {
    return "…" + (if (isSticky) "$" else "")
  }
}