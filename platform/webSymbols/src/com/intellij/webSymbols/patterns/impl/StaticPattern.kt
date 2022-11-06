// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider

internal class StaticPattern(val content: String) : WebSymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(content)

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     itemsProvider: WebSymbolsPatternItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    if (content.length <= end - start && params.name.startsWith(content, start))
      listOf(MatchResult(WebSymbolNameSegment(start, start + content.length)))
    else emptyList()

  override fun getCompletionResults(owner: WebSymbol?,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    itemsProvider: WebSymbolsPatternItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    CompletionResults(WebSymbolCodeCompletionItem.create(content, start))

  override fun toString(): String =
    "\"${content}\""
}