package com.intellij.webSymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.search.impl.WebSymbolSearchTargetImpl

interface WebSymbolSearchTarget : SearchTarget {

  val symbol: WebSymbol

  override fun createPointer(): Pointer<out WebSymbolSearchTarget>

  companion object {
    fun create(symbol: WebSymbol): WebSymbolSearchTarget =
      WebSymbolSearchTargetImpl(symbol)
  }

}