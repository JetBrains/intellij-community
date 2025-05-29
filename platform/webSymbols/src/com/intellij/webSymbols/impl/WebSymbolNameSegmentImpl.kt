// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.utils.matchedNameOrName

class WebSymbolNameSegmentImpl internal constructor(
  override val start: Int,
  override val end: Int,
  override val symbols: List<PolySymbol>,
  override val problem: WebSymbolNameSegment.MatchProblem?,
  override val displayName: @NlsSafe String?,
  override val matchScore: Int,
  symbolKinds: Set<PolySymbolQualifiedKind>?,
  private val explicitApiStatus: PolySymbolApiStatus?,
  private val explicitPriority: PolySymbol.Priority?,
  private val explicitProximity: Int?,
  internal val highlightingEnd: Int?,
) : WebSymbolNameSegment {

  init {
    assert(start <= end)
  }

  private val forcedSymbolKinds = symbolKinds

  override val apiStatus: PolySymbolApiStatus?
    get() = explicitApiStatus

  override val priority: PolySymbol.Priority?
    get() = explicitPriority ?: symbols.asSequence().mapNotNull { it.priority }.maxOrNull()

  override val proximity: Int?
    get() = explicitProximity ?: symbols.asSequence().mapNotNull { it.proximity }.maxOrNull()

  override val symbolKinds: Set<PolySymbolQualifiedKind>
    get() =
      forcedSymbolKinds
      ?: symbols.asSequence().map { PolySymbolQualifiedKind(it.namespace, it.kind) }.toSet()

  override fun getName(symbol: PolySymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  internal fun withOffset(offset: Int): WebSymbolNameSegmentImpl =
    WebSymbolNameSegmentImpl(start + offset, end + offset, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity,
                             highlightingEnd?.let { it + offset })

  internal fun withDisplayName(displayName: String?) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, this.displayName ?: displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity,
                             highlightingEnd)

  internal fun withRange(start: Int, end: Int) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity,
                             null)

  internal fun withSymbols(symbols: List<PolySymbol>) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity,
                             highlightingEnd)

  internal fun copy(
    apiStatus: PolySymbolApiStatus?,
    priority: PolySymbol.Priority?,
    proximity: Int?,
    problem: WebSymbolNameSegment.MatchProblem?,
    symbols: List<PolySymbol>,
    highlightEnd: Int? = null,
  ): WebSymbolNameSegmentImpl =
    WebSymbolNameSegmentImpl(start, end, this.symbols + symbols, problem ?: this.problem,
                             displayName, matchScore, forcedSymbolKinds,
                             apiStatus ?: this.explicitApiStatus, priority ?: this.explicitPriority,
                             proximity ?: this.explicitProximity, highlightEnd ?: this.highlightingEnd)

  internal fun canUnwrapSymbols(): Boolean =
    explicitApiStatus == null
    && problem == null
    && displayName == null
    && matchScore == end - start
    && explicitPriority == null
    && explicitProximity == null
    && symbols.isNotEmpty()

  override fun createPointer(): Pointer<WebSymbolNameSegment> =
    NameSegmentPointer(this)

  override fun toString(): String =
    "<$start:$end${if (problem != null) ":$problem" else ""}-${symbols.size}cs>"


  private class NameSegmentPointer(nameSegment: WebSymbolNameSegmentImpl) : Pointer<WebSymbolNameSegment> {

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
    private val explicitProximity = nameSegment.explicitProximity
    private val highlightingEnd = nameSegment.highlightingEnd

    override fun dereference(): WebSymbolNameSegmentImpl? =
      symbols.map { it.dereference() }
        .takeIf { it.all { symbol -> symbol != null } }
        ?.let {
          @Suppress("UNCHECKED_CAST")
          (WebSymbolNameSegmentImpl(start, end, it as List<PolySymbol>, problem, displayName, matchScore,
                                    types, explicitApiStatus, explicitPriority, explicitProximity, highlightingEnd))
        }

  }
}