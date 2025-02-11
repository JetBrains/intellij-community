// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.impl.WebSymbolNameSegmentImpl
import com.intellij.webSymbols.utils.matchedNameOrName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface WebSymbolNameSegment {
  val start: Int

  val end: Int

  val symbols: List<WebSymbol>

  val problem: MatchProblem?

  val displayName: @NlsSafe String?

  val matchScore: Int

  val apiStatus: WebSymbolApiStatus?

  val priority: WebSymbol.Priority?

  val proximity: Int?

  val symbolKinds: Set<WebSymbolQualifiedKind>

  fun getName(symbol: WebSymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  fun createPointer(): Pointer<WebSymbolNameSegment>

  companion object {

    fun create(symbol: WebSymbol): WebSymbolNameSegment =
      create(0, symbol.name.length, listOf(symbol))

    fun create(start: Int, end: Int, symbol: WebSymbol): WebSymbolNameSegment =
      create(start, end, listOf(symbol))

    fun create(start: Int, end: Int, vararg symbols: WebSymbol): WebSymbolNameSegment =
      create(start, end, symbols.toList())

    fun create(
      start: Int,
      end: Int,
      symbols: List<WebSymbol> = emptyList(),
      problem: MatchProblem? = null,
      displayName: @NlsSafe String? = null,
      matchScore: Int = end - start,
      symbolKinds: Set<WebSymbolQualifiedKind>? = null,
      explicitApiStatus: WebSymbolApiStatus? = null,
      explicitPriority: WebSymbol.Priority? = null,
      explicitProximity: Int? = null,
    ): WebSymbolNameSegment =
      WebSymbolNameSegmentImpl(start, end, symbols, problem, displayName, matchScore,
                               symbolKinds, explicitApiStatus, explicitPriority, explicitProximity, null)
  }

  enum class MatchProblem {
    MISSING_REQUIRED_PART,
    UNKNOWN_SYMBOL,
    DUPLICATE
  }

}