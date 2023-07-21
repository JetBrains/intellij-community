package com.intellij.webSymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.search.impl.WebSymbolSearchTargetImpl

interface WebSymbolSearchTarget : SearchTarget {

  val symbol: WebSymbol

  companion object {
    fun create(symbol: WebSymbol) =
      WebSymbolSearchTargetImpl(symbol)
  }

}