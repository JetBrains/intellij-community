// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.query.WebSymbolMatch

internal class ListResult(
  val name: String,
  segments: List<WebSymbolNameSegment>,
) : MatchResult(segments) {

  constructor(name: String, segment: WebSymbolNameSegment) :
    this(name, listOf(segment.unpackIfPossible(name)))

  fun prefixedWith(prevResult: ListResult): ListResult =
    ListResult(prevResult.name + name, prevResult.segments + segments.map { it.withOffset(prevResult.name.length) })

  override fun toString(): String {
    return "$name $segments"
  }
}

private fun WebSymbolNameSegment.unpackIfPossible(name: String): WebSymbolNameSegment {
  if (!canUnwrapSymbols() || symbols.size != 1) return this
  val symbol = symbols[0] as? WebSymbolMatch ?: return this
  return if (symbol.name == name && symbol.nameSegments.size == 1)
    symbol.nameSegments[0]
  else
    this
}
