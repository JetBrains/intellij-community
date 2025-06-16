// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.impl.*
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.util.containers.Stack
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class PolySymbolPattern internal constructor() {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(
    owner: PolySymbol?,
    scope: Stack<PolySymbolScope>,
    name: String,
    params: PolySymbolNameMatchQueryParams,
  ): List<MatchResult> =
    match(owner, scope, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun list(
    owner: PolySymbol?,
    scope: Stack<PolySymbolScope>,
    params: PolySymbolListSymbolsQueryParams,
  ): List<ListResult> =
    list(owner, scope, null, ListParameters(params))
      .map { it.removeEmptySegments() }

  internal fun complete(
    owner: PolySymbol?,
    scope: Stack<PolySymbolScope>,
    name: String,
    params: PolySymbolCodeCompletionQueryParams,
  ): List<PolySymbolCodeCompletionItem> =
    complete(owner, Stack(scope), null,
             CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolScope>,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters, start: Int, end: Int,
  ): List<MatchResult>

  internal abstract fun list(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolScope>,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult>

  internal abstract fun complete(
    owner: PolySymbol?,
    scopeStack: Stack<PolySymbolScope>,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters, start: Int, end: Int,
  ): CompletionResults

}