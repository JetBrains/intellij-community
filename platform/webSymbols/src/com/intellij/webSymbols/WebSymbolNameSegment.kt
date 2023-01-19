// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.webSymbols.utils.matchedNameOrName

class WebSymbolNameSegment(val start: Int,
                           val end: Int,
                           val symbols: List<WebSymbol> = emptyList(),
                           val problem: MatchProblem? = null,
                           val displayName: @NlsSafe String? = null,
                           val matchScore: Int = end - start,
                           symbolKinds: Set<WebSymbolQualifiedKind>? = null,
                           private val explicitDeprecated: Boolean? = null,
                           private val explicitPriority: WebSymbol.Priority? = null,
                           private val explicitProximity: Int? = null) {

  constructor(symbol: WebSymbol): this(0, symbol.name.length, listOf(symbol))

  constructor(start: Int, end: Int, symbol: WebSymbol) : this(start, end, listOf(symbol))
  constructor(start: Int, end: Int, vararg symbols: WebSymbol) : this(start, end, symbols.toList())

  init {
    assert(start <= end)
  }

  private val forcedSymbolTypes = symbolKinds

  @get:JvmName("isDeprecated")
  val deprecated: Boolean
    get() = explicitDeprecated ?: symbols.any { it.deprecated }

  val priority: WebSymbol.Priority?
    get() = explicitPriority ?: symbols.asSequence().mapNotNull { it.priority }.maxOrNull()

  val proximity: Int?
    get() = explicitProximity ?: symbols.asSequence().mapNotNull { it.proximity }.maxOrNull()

  val symbolKinds: Set<WebSymbolQualifiedKind>
    get() =
      forcedSymbolTypes
      ?: symbols.asSequence().map { WebSymbolQualifiedKind(it.namespace, it.kind) }.toSet()

  fun getName(symbol: WebSymbol): @NlsSafe String =
    symbol.matchedNameOrName.substring(start, end)

  internal fun withOffset(offset: Int): WebSymbolNameSegment =
    WebSymbolNameSegment(start + offset, end + offset, symbols, problem, displayName,
                         matchScore, symbolKinds, explicitDeprecated, explicitPriority, explicitProximity)

  internal fun copy(deprecated: Boolean? = null,
                    priority: WebSymbol.Priority? = null,
                    proximity: Int? = null,
                    problem: MatchProblem? = null,
                    symbols: List<WebSymbol> = emptyList()): WebSymbolNameSegment =
    WebSymbolNameSegment(start, end, this.symbols + symbols, problem ?: this.problem,
                         displayName, matchScore, symbolKinds,
                         deprecated ?: this.explicitDeprecated, priority ?: this.explicitPriority,
                         proximity ?: this.explicitProximity)

  internal fun canUnwrapSymbols(): Boolean =
    explicitDeprecated == null
    && problem == null
    && displayName == null
    && matchScore == end - start
    && explicitPriority == null
    && explicitProximity == null
    && symbols.isNotEmpty()

  fun createPointer(): Pointer<WebSymbolNameSegment> =
    NameSegmentPointer(this)

  override fun toString(): String =
    "<$start:$end${if (problem != null) ":$problem" else ""}-${symbols.size}cs>"

  enum class MatchProblem {
    MISSING_REQUIRED_PART,
    UNKNOWN_SYMBOL,
    DUPLICATE
  }

  private class NameSegmentPointer(nameSegment: WebSymbolNameSegment) : Pointer<WebSymbolNameSegment> {

    private val start = nameSegment.start
    private val end = nameSegment.end
    private val symbols = nameSegment.symbols.map { it.createPointer() }
    private val problem = nameSegment.problem

    @NlsSafe
    private val displayName = nameSegment.displayName
    private val matchScore = nameSegment.matchScore
    private val types = nameSegment.symbolKinds
    private val explicitDeprecated = nameSegment.explicitDeprecated
    private val explicitPriority = nameSegment.explicitPriority
    private val explicitProximity = nameSegment.explicitProximity


    override fun dereference(): WebSymbolNameSegment? =
      symbols.map { it.dereference() }
        .takeIf { it.all { symbol -> symbol != null } }
        ?.let {
          @Suppress("UNCHECKED_CAST")
          (WebSymbolNameSegment(start, end, it as List<WebSymbol>, problem, displayName, matchScore,
                                types, explicitDeprecated, explicitPriority, explicitProximity))
        }

  }
}