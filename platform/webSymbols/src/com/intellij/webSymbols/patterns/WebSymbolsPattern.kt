// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.registry.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.registry.WebSymbolsNameMatchQueryParams

abstract class WebSymbolsPattern {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(owner: WebSymbol?,
                     context: Stack<WebSymbolsContainer>,
                     name: String,
                     params: WebSymbolsNameMatchQueryParams): List<MatchResult> =
    match(owner, context, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun getCompletionResults(owner: WebSymbol?,
                                    context: Stack<WebSymbolsContainer>,
                                    name: String,
                                    params: WebSymbolsCodeCompletionQueryParams): List<WebSymbolCodeCompletionItem> =
    getCompletionResults(owner, Stack(context), null,
                         CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(owner: WebSymbol?,
                              contextStack: Stack<WebSymbolsContainer>,
                              itemsProvider: WebSymbolsPatternItemsProvider?,
                              params: MatchParameters, start: Int, end: Int): List<MatchResult>

  internal abstract fun getCompletionResults(owner: WebSymbol?,
                                             contextStack: Stack<WebSymbolsContainer>,
                                             itemsProvider: WebSymbolsPatternItemsProvider?,
                                             params: CompletionParameters, start: Int, end: Int): CompletionResults

}