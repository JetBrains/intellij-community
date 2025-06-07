// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.impl.*
import com.intellij.polySymbols.query.PolySymbolsCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class PolySymbolsPattern internal constructor() {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(owner: PolySymbol?,
                     scope: Stack<PolySymbolsScope>,
                     name: String,
                     params: PolySymbolsNameMatchQueryParams): List<MatchResult> =
    match(owner, scope, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun list(owner: PolySymbol?,
                    scope: Stack<PolySymbolsScope>,
                    params: PolySymbolsListSymbolsQueryParams): List<ListResult> =
    list(owner, scope, null, ListParameters(params))
      .map { it.removeEmptySegments() }

  internal fun complete(owner: PolySymbol?,
                        scope: Stack<PolySymbolsScope>,
                        name: String,
                        params: PolySymbolsCodeCompletionQueryParams): List<PolySymbolCodeCompletionItem> =
    complete(owner, Stack(scope), null,
             CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(owner: PolySymbol?,
                              scopeStack: Stack<PolySymbolsScope>,
                              symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                              params: MatchParameters, start: Int, end: Int): List<MatchResult>

  internal abstract fun list(owner: PolySymbol?,
                             scopeStack: Stack<PolySymbolsScope>,
                             symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                             params: ListParameters): List<ListResult>

  internal abstract fun complete(owner: PolySymbol?,
                                 scopeStack: Stack<PolySymbolsScope>,
                                 symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                                 params: CompletionParameters, start: Int, end: Int): CompletionResults

}