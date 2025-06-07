// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.impl.PolySymbolNameSegmentImpl
import com.intellij.polySymbols.utils.matchedNameOrName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolNameSegment {
  val start: Int

  val end: Int

  val symbols: List<PolySymbol>

  val problem: MatchProblem?

  val displayName: @NlsSafe String?

  val matchScore: Int

  val apiStatus: PolySymbolApiStatus?

  val priority: PolySymbol.Priority?

  val symbolKinds: Set<PolySymbolQualifiedKind>

  fun getName(symbol: PolySymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  fun createPointer(): Pointer<PolySymbolNameSegment>

  companion object {

    fun create(symbol: PolySymbol): PolySymbolNameSegment =
      create(0, symbol.name.length, listOf(symbol))

    fun create(start: Int, end: Int, symbol: PolySymbol): PolySymbolNameSegment =
      create(start, end, listOf(symbol))

    fun create(start: Int, end: Int, vararg symbols: PolySymbol): PolySymbolNameSegment =
      create(start, end, symbols.toList())

    fun create(
      start: Int,
      end: Int,
      symbols: List<PolySymbol> = emptyList(),
      problem: MatchProblem? = null,
      displayName: @NlsSafe String? = null,
      matchScore: Int = end - start,
      symbolKinds: Set<PolySymbolQualifiedKind>? = null,
      explicitApiStatus: PolySymbolApiStatus? = null,
      explicitPriority: PolySymbol.Priority? = null,
    ): PolySymbolNameSegment =
      PolySymbolNameSegmentImpl(start, end, symbols, problem, displayName, matchScore,
                                symbolKinds, explicitApiStatus, explicitPriority, null)
  }

  enum class MatchProblem {
    MISSING_REQUIRED_PART,
    UNKNOWN_SYMBOL,
    DUPLICATE
  }

}