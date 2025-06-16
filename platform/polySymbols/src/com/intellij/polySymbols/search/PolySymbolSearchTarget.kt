package com.intellij.polySymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.search.impl.PolySymbolSearchTargetImpl

interface PolySymbolSearchTarget : SearchTarget {

  val symbol: PolySymbol

  override fun createPointer(): Pointer<out PolySymbolSearchTarget>

  companion object {
    fun create(symbol: PolySymbol): PolySymbolSearchTarget =
      PolySymbolSearchTargetImpl(symbol)
  }
}