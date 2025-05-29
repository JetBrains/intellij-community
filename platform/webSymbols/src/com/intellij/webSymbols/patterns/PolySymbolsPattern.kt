// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class PolySymbolsPattern internal constructor() {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(owner: PolySymbol?,
                     scope: Stack<PolySymbolsScope>,
                     name: String,
                     params: WebSymbolsNameMatchQueryParams): List<MatchResult> =
    match(owner, scope, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun list(owner: PolySymbol?,
                    scope: Stack<PolySymbolsScope>,
                    params: WebSymbolsListSymbolsQueryParams): List<ListResult> =
    list(owner, scope, null, ListParameters(params))
      .map { it.removeEmptySegments() }

  internal fun complete(owner: PolySymbol?,
                        scope: Stack<PolySymbolsScope>,
                        name: String,
                        params: WebSymbolsCodeCompletionQueryParams): List<PolySymbolCodeCompletionItem> =
    complete(owner, Stack(scope), null,
             CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(owner: PolySymbol?,
                              scopeStack: Stack<PolySymbolsScope>,
                              symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                              params: MatchParameters, start: Int, end: Int): List<MatchResult>

  internal abstract fun list(owner: PolySymbol?,
                             scopeStack: Stack<PolySymbolsScope>,
                             symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                             params: ListParameters): List<ListResult>

  internal abstract fun complete(owner: PolySymbol?,
                                 scopeStack: Stack<PolySymbolsScope>,
                                 symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                 params: CompletionParameters, start: Int, end: Int): CompletionResults

}