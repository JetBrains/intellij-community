package com.intellij.webSymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.search.impl.PolySymbolSearchTargetImpl

interface PolySymbolSearchTarget : SearchTarget {

  val symbol: PolySymbol

  override fun createPointer(): Pointer<out PolySymbolSearchTarget>

  companion object {
    fun create(symbol: PolySymbol): PolySymbolSearchTarget =
      PolySymbolSearchTargetImpl(symbol)
  }

}