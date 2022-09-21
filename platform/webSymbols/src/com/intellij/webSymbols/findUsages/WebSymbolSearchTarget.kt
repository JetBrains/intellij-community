package com.intellij.webSymbols.findUsages

import com.intellij.find.usages.api.SearchTarget
import com.intellij.webSymbols.WebSymbol

interface WebSymbolSearchTarget : SearchTarget {

  val symbol: WebSymbol

}