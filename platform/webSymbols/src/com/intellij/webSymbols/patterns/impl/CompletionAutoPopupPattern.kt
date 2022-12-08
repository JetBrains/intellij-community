// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver
import com.intellij.webSymbols.utils.hideFromCompletion

internal class CompletionAutoPopupPattern(val isSticky: Boolean) : WebSymbolsPattern() {

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    listOf(MatchResult(WebSymbolNameSegment(start, start)))

  override fun getCompletionResults(owner: WebSymbol?,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    if (symbolsResolver == null || symbolsResolver.delegate?.hideFromCompletion == true) {
      CompletionResults(emptyList(), true)
    }
    else {
      CompletionResults(WebSymbolCodeCompletionItem.create("", start, true, displayName = "…"))
    }

  override fun toString(): String {
    return "…" + (if (isSticky) "$" else "")
  }
}