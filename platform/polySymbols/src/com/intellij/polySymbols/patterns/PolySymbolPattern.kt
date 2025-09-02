// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.impl.*
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class PolySymbolPattern internal constructor() {

  internal abstract fun getStaticPrefixes(): Sequence<String>

  internal open fun isStaticAndRequired(): Boolean = true

  internal fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    name: String,
    params: PolySymbolNameMatchQueryParams,
  ): List<MatchResult> =
    match(owner, stack, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  internal fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    params: PolySymbolListSymbolsQueryParams,
  ): List<ListResult> =
    list(owner, stack, null, ListParameters(params))
      .map { it.removeEmptySegments() }

  internal fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    name: String,
    params: PolySymbolCodeCompletionQueryParams,
  ): List<PolySymbolCodeCompletionItem> =
    complete(owner, stack.copy(), null,
             CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters, start: Int, end: Int,
  ): List<MatchResult>

  internal abstract fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult>

  internal abstract fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters, start: Int, end: Int,
  ): CompletionResults

}