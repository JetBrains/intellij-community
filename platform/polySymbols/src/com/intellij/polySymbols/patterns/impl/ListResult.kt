// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.impl.canUnwrapSymbols
import com.intellij.polySymbols.impl.withOffset
import com.intellij.polySymbols.query.PolySymbolMatch

internal class ListResult(
  val name: String,
  segments: List<PolySymbolNameSegment>,
) : MatchResult(segments) {

  constructor(name: String, segment: PolySymbolNameSegment) :
    this(name, listOf(segment.unpackIfPossible(name)))

  fun prefixedWith(prevResult: ListResult): ListResult =
    ListResult(prevResult.name + name, prevResult.segments + segments.map { it.withOffset(prevResult.name.length) })

  override fun toString(): String {
    return "$name $segments"
  }
}

private fun PolySymbolNameSegment.unpackIfPossible(name: String): PolySymbolNameSegment {
  if (!canUnwrapSymbols() || symbols.size != 1) return this
  val symbol = symbols[0] as? PolySymbolMatch ?: return this
  return if (symbol.name == name && symbol.nameSegments.size == 1)
    symbol.nameSegments[0]
  else
    this
}
