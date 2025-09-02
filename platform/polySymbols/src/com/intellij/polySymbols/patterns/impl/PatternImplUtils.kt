// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.impl.PolySymbolCodeCompletionItemImpl
import com.intellij.polySymbols.impl.copy
import com.intellij.polySymbols.query.PolySymbolQueryStack

internal fun PolySymbolCodeCompletionItem.withStopSequencePatternEvaluation(stop: Boolean): PolySymbolCodeCompletionItem =
  if ((this as PolySymbolCodeCompletionItemImpl).stopSequencePatternEvaluation != stop)
    this.copy(stopSequencePatternEvaluation = stop)
  else this

internal val PolySymbolCodeCompletionItem.stopSequencePatternEvaluation
  get() = (this as PolySymbolCodeCompletionItemImpl).stopSequencePatternEvaluation

internal fun <T : MatchResult> T.addOwner(owner: PolySymbol): T {
  val newSegments = mutableListOf<PolySymbolNameSegment>()
  var foundNonEmpty = false
  var applied = false
  for (segment in segments) {
    if (segment.symbols.contains(owner))
      return this
    if (segment.problem != null) {
      foundNonEmpty = true
      applied = true
      newSegments.add(segment)
      continue
    }
    if (segment.start == segment.end && segment.symbols.isEmpty()) {
      continue
    }
    if (foundNonEmpty || segment.symbols.isNotEmpty()) {
      foundNonEmpty = true
      newSegments.add(segment)
    }
    else {
      newSegments.add(segment.copy(symbols = listOf(owner), highlightEnd = end.takeIf { !applied }))
      applied = true
    }
  }
  if (!applied) {
    newSegments.add(0, PolySymbolNameSegment.create(start, start, owner).copy(highlightEnd = end))
  }
  return copy(segments = newSegments)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : MatchResult> T.copy(segments: List<PolySymbolNameSegment>): T =
  when (this) {
    is ListResult -> ListResult(name, segments)
    else -> MatchResult(segments)
  } as T

internal fun List<PolySymbolCodeCompletionItem>.applyIcons(symbol: PolySymbol) =
  if (symbol.icon != null) {
    map { item -> if (item.icon == null) item.withIcon(symbol.icon) else item }
  }
  else if (symbol.origin.defaultIcon != null) {
    map { item -> if (item.icon == null) item.withIcon(symbol.origin.defaultIcon) else item }
  }
  else {
    this
  }

private val SPECIAL_CHARS = setOf('[', '.', '\\', '^', '$', '(', '+')
private val SPECIAL_CHARS_ONE_BACK = setOf('?', '*', '{')

internal const val SPECIAL_MATCHED_CONTRIB = "\$special$"

internal fun getPatternCompletablePrefix(pattern: String?): String {
  if (pattern == null || pattern.contains('|')) return ""
  for (i in 0 until pattern.length) {
    val char = pattern[i]
    if (SPECIAL_CHARS.contains(char)) {
      return pattern.substring(0 until i)
    }
    else if (SPECIAL_CHARS_ONE_BACK.contains(char)) {
      return if (i < 1) "" else pattern.substring(0 until i - 1)
    }
  }
  return pattern
}

internal fun <T> withPrevMatchScope(
  scopeStack: PolySymbolQueryStack,
  prevResult: List<PolySymbolNameSegment>?,
  action: () -> T,
): T =
  if (prevResult.isNullOrEmpty()) {
    ProgressManager.checkCanceled()
    action()
  }
  else {
    ProgressManager.checkCanceled()
    val additionalScope = prevResult
      .flatMap { it.symbols }
      .flatMap { it.queryScope }
    scopeStack.withSymbols(additionalScope) {
      action()
    }
  }

internal fun <T : MatchResult> T.applyToSegments(
  vararg contributions: PolySymbol,
  apiStatus: PolySymbolApiStatus? = null,
  priority: PolySymbol.Priority? = null,
): T =
  if (apiStatus != null || priority != null || contributions.isNotEmpty())
    copy(
      segments.map {
        it.copy(apiStatus = apiStatus, priority = priority, symbols = contributions.toList())
      })
  else
    this

internal fun ListResult.removeEmptySegments(): ListResult =
  ListResult(
    name,
    segments.filter { !it.isEmpty() }
      .ifEmpty { listOf(segments.first()) }
  )

internal fun MatchResult.removeEmptySegments(): MatchResult =
  MatchResult(
    segments.filter { !it.isEmpty() }
      .ifEmpty { listOf(segments.first()) }
  )

internal fun PolySymbolNameSegment.isEmpty() =
  start == end && problem == null && symbols.isEmpty() && apiStatus == null

internal fun MatchResult.prefixedWith(prevResult: MatchResult?): MatchResult =
  prevResult?.let { MatchResult(it.segments + this.segments) }
  ?: this
