package com.intellij.polySymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.search.impl.PolySymbolSearchTargetImpl
import com.intellij.find.usages.api.UsageSearcher

/**
 * A specialized [SearchTarget], which provides the [PolySymbol],
 * to which references are being searched, for the Poly Symbol framework.
 * It allows running the search through generic [UsageSearcher] and handling
 * various edge cases through the framework APIs.
 */
interface PolySymbolSearchTarget : SearchTarget {

  val symbol: PolySymbol

  override fun createPointer(): Pointer<out PolySymbolSearchTarget>

  companion object {
    fun create(symbol: PolySymbol): PolySymbolSearchTarget =
      PolySymbolSearchTargetImpl(symbol)
  }
}