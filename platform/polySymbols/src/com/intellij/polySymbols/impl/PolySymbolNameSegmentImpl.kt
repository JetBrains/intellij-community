// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.utils.matchedNameOrName
import org.jetbrains.annotations.ApiStatus

class PolySymbolNameSegmentImpl internal constructor(
  override val start: Int,
  override val end: Int,
  override val symbols: List<PolySymbol>,
  override val problem: PolySymbolNameSegment.MatchProblem?,
  override val displayName: @NlsSafe String?,
  override val matchScore: Int,
  symbolKinds: Set<PolySymbolQualifiedKind>?,
  private val explicitApiStatus: PolySymbolApiStatus?,
  private val explicitPriority: PolySymbol.Priority?,
  @ApiStatus.Internal val highlightingEnd: Int?,
) : PolySymbolNameSegment {

  init {
    assert(start <= end)
  }

  private val forcedSymbolKinds = symbolKinds

  override val apiStatus: PolySymbolApiStatus?
    get() = explicitApiStatus

  override val priority: PolySymbol.Priority?
    get() = explicitPriority ?: symbols.asSequence().mapNotNull { it.priority }.maxOrNull()

  override val symbolKinds: Set<PolySymbolQualifiedKind>
    get() =
      forcedSymbolKinds
      ?: symbols.asSequence().map { it.qualifiedKind }.toSet()

  override fun getName(symbol: PolySymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  internal fun withOffset(offset: Int): PolySymbolNameSegmentImpl =
    PolySymbolNameSegmentImpl(start + offset, end + offset, symbols, problem, displayName,
                              matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, highlightingEnd?.let { it + offset })

  internal fun withDisplayName(displayName: String?) =
    PolySymbolNameSegmentImpl(start, end, symbols, problem, this.displayName ?: displayName,
                              matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, highlightingEnd)

  internal fun withRange(start: Int, end: Int) =
    PolySymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                              matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, null)

  internal fun withSymbols(symbols: List<PolySymbol>) =
    PolySymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                              matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, highlightingEnd)

  internal fun copy(
    apiStatus: PolySymbolApiStatus?,
    priority: PolySymbol.Priority?,
    proximity: Int?,
    problem: PolySymbolNameSegment.MatchProblem?,
    symbols: List<PolySymbol>,
    highlightEnd: Int? = null,
  ): PolySymbolNameSegmentImpl =
    PolySymbolNameSegmentImpl(start, end, this.symbols + symbols, problem ?: this.problem,
                              displayName, matchScore, forcedSymbolKinds,
                              apiStatus ?: this.explicitApiStatus, priority ?: this.explicitPriority,
                              highlightEnd ?: this.highlightingEnd)

  internal fun canUnwrapSymbols(): Boolean =
    explicitApiStatus == null
    && problem == null
    && displayName == null
    && matchScore == end - start
    && explicitPriority == null
    && symbols.isNotEmpty()

  override fun createPointer(): Pointer<PolySymbolNameSegment> =
    NameSegmentPointer(this)

  override fun toString(): String =
    "<$start:$end${if (problem != null) ":$problem" else ""}-${symbols.size}cs>"


  private class NameSegmentPointer(nameSegment: PolySymbolNameSegmentImpl) : Pointer<PolySymbolNameSegment> {

    private val start = nameSegment.start
    private val end = nameSegment.end
    private val symbols = nameSegment.symbols.map { it.createPointer() }
    private val problem = nameSegment.problem

    @NlsSafe
    private val displayName = nameSegment.displayName
    private val matchScore = nameSegment.matchScore
    private val types = nameSegment.symbolKinds
    private val explicitApiStatus = nameSegment.explicitApiStatus
    private val explicitPriority = nameSegment.explicitPriority
    private val highlightingEnd = nameSegment.highlightingEnd

    override fun dereference(): PolySymbolNameSegmentImpl? =
      symbols.map { it.dereference() }
        .takeIf { it.all { symbol -> symbol != null } }
        ?.let {
          @Suppress("UNCHECKED_CAST")
          (PolySymbolNameSegmentImpl(start, end, it as List<PolySymbol>, problem, displayName, matchScore,
                                     types, explicitApiStatus, explicitPriority, highlightingEnd))
        }

  }
}