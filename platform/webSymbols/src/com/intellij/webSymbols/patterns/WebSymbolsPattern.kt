// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams

abstract class WebSymbolsPattern {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(owner: WebSymbol?,
                     scope: Stack<WebSymbolsScope>,
                     name: String,
                     params: WebSymbolsNameMatchQueryParams): List<MatchResult> =
    match(owner, scope, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun list(owner: WebSymbol?,
                    scope: Stack<WebSymbolsScope>,
                    params: WebSymbolsListSymbolsQueryParams): List<ListResult> =
    list(owner, scope, null, ListParameters(params))
      .map { it.removeEmptySegments() }

  internal fun complete(owner: WebSymbol?,
                        scope: Stack<WebSymbolsScope>,
                        name: String,
                        params: WebSymbolsCodeCompletionQueryParams): List<WebSymbolCodeCompletionItem> =
    complete(owner, Stack(scope), null,
             CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(owner: WebSymbol?,
                              scopeStack: Stack<WebSymbolsScope>,
                              symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                              params: MatchParameters, start: Int, end: Int): List<MatchResult>

  internal abstract fun list(owner: WebSymbol?,
                             scopeStack: Stack<WebSymbolsScope>,
                             symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                             params: ListParameters): List<ListResult>

  internal abstract fun complete(owner: WebSymbol?,
                                 scopeStack: Stack<WebSymbolsScope>,
                                 symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                 params: CompletionParameters, start: Int, end: Int): CompletionResults

}