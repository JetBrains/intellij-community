// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.impl.WebSymbolCodeCompletionItemImpl

internal fun WebSymbolCodeCompletionItem.withStopSequencePatternEvaluation(stop: Boolean): WebSymbolCodeCompletionItem =
  if ((this as WebSymbolCodeCompletionItemImpl).stopSequencePatternEvaluation != stop)
    this.copy(stopSequencePatternEvaluation = stop)
  else this

internal val WebSymbolCodeCompletionItem.stopSequencePatternEvaluation
  get() = (this as WebSymbolCodeCompletionItemImpl).stopSequencePatternEvaluation

internal fun MatchResult.addOwner(owner: WebSymbol): MatchResult {
  val newSegments = mutableListOf<WebSymbolNameSegment>()
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
      applied = true
      newSegments.add(segment.copy(symbols = listOf(owner)))
    }
  }
  if (!applied) {
    newSegments.add(0, WebSymbolNameSegment(start, start, owner))
  }
  return MatchResult(newSegments)
}

internal fun List<WebSymbolCodeCompletionItem>.applyIcons(symbol: WebSymbol) =
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
  for (i in 0..pattern.length) {
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

internal fun <T> withPrevMatchScope(scopeStack: Stack<WebSymbolsScope>,
                                    prevResult: List<WebSymbolNameSegment>?,
                                    action: () -> T): T =
  if (prevResult == null) {
    action()
  }
  else {
    val additionalScope = prevResult
      .flatMap { it.symbols }
      .flatMap { it.queryScope }
    scopeStack.addAll(additionalScope)
    try {
      action()
    }
    finally {
      repeat(additionalScope.size) { scopeStack.pop() }
    }
  }

internal fun MatchResult.applyToSegments(vararg contributions: WebSymbol,
                                         deprecated: Boolean? = null,
                                         priority: WebSymbol.Priority? = null,
                                         proximity: Int? = null): MatchResult =
  if (deprecated != null || priority != null || proximity != null || contributions.isNotEmpty())
    MatchResult(
      segments.map {
        it.copy(deprecated = deprecated, priority = priority, proximity = proximity, symbols = contributions.toList())
      })
  else
    this

internal fun MatchResult.removeEmptySegments(): MatchResult =
  MatchResult(
    segments.filter {
      it.start != it.end
      || it.problem != null
      || it.symbols.isNotEmpty()
      || it.deprecated
      || it.proximity != null
    }.ifEmpty { listOf(segments.first()) })

internal fun MatchResult.prefixedWith(prevResult: MatchResult?): MatchResult =
  prevResult?.let { MatchResult(it.segments + this.segments) }
  ?: this