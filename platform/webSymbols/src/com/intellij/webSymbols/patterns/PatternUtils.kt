// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.WebSymbolsPattern.MatchResult

internal fun MatchResult.addOwner(owner: WebSymbol): MatchResult {
  val newSegments = mutableListOf<WebSymbol.NameSegment>()
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
    if (segment.start == segment.end) {
      continue
    }
    if (foundNonEmpty || segment.symbols.isNotEmpty()) {
      foundNonEmpty = true
      newSegments.add(segment)
    }
    else {
      applied = true
      newSegments.add(segment.applyProperties(symbols = listOf(owner)))
    }
  }
  if (!applied) {
    newSegments.add(0, WebSymbol.NameSegment(start, start, owner))
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
