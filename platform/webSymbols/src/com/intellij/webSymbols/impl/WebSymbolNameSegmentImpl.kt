// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.utils.matchedNameOrName

class WebSymbolNameSegmentImpl internal constructor(
  override val start: Int,
  override val end: Int,
  override val symbols: List<WebSymbol>,
  override val problem: WebSymbolNameSegment.MatchProblem?,
  override val displayName: @NlsSafe String?,
  override val matchScore: Int,
  symbolKinds: Set<WebSymbolQualifiedKind>?,
  private val explicitApiStatus: WebSymbolApiStatus?,
  private val explicitPriority: WebSymbol.Priority?,
  private val explicitProximity: Int?
): WebSymbolNameSegment {

  init {
    assert(start <= end)
  }

  private val forcedSymbolKinds = symbolKinds

  override val apiStatus: WebSymbolApiStatus?
    get() = explicitApiStatus

  override val priority: WebSymbol.Priority?
    get() = explicitPriority ?: symbols.asSequence().mapNotNull { it.priority }.maxOrNull()

  override val proximity: Int?
    get() = explicitProximity ?: symbols.asSequence().mapNotNull { it.proximity }.maxOrNull()

  override val symbolKinds: Set<WebSymbolQualifiedKind>
    get() =
      forcedSymbolKinds
      ?: symbols.asSequence().map { WebSymbolQualifiedKind(it.namespace, it.kind) }.toSet()

  override fun getName(symbol: WebSymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  internal fun withOffset(offset: Int): WebSymbolNameSegmentImpl =
    WebSymbolNameSegmentImpl(start + offset, end + offset, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity)

  internal fun withDisplayName(displayName: String?) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, this.displayName ?: displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity)

  internal fun withRange(start: Int, end: Int) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity)

  internal fun withSymbols(symbols: List<WebSymbol>) =
    WebSymbolNameSegmentImpl(start, end, symbols, problem, displayName,
                             matchScore, forcedSymbolKinds, explicitApiStatus, explicitPriority, explicitProximity)

  internal fun copy(
    apiStatus: WebSymbolApiStatus?,
    priority: WebSymbol.Priority?,
    proximity: Int?,
    problem: WebSymbolNameSegment.MatchProblem?,
    symbols: List<WebSymbol>,
  ): WebSymbolNameSegmentImpl =
    WebSymbolNameSegmentImpl(start, end, this.symbols + symbols, problem ?: this.problem,
                             displayName, matchScore, forcedSymbolKinds,
                         apiStatus ?: this.explicitApiStatus, priority ?: this.explicitPriority,
                         proximity ?: this.explicitProximity)

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


    override fun dereference(): WebSymbolNameSegmentImpl? =
      symbols.map { it.dereference() }
        .takeIf { it.all { symbol -> symbol != null } }
        ?.let {
          @Suppress("UNCHECKED_CAST")
          (WebSymbolNameSegmentImpl(start, end, it as List<WebSymbol>, problem, displayName, matchScore,
                                    types, explicitApiStatus, explicitPriority, explicitProximity))
        }

  }
}