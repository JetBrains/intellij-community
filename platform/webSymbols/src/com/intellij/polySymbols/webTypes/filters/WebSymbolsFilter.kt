// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.filters

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.webTypes.impl.WebSymbolsFilterEP

interface WebSymbolsFilter {

  fun filterCodeCompletions(codeCompletions: List<PolySymbolCodeCompletionItem>,
                            queryExecutor: PolySymbolsQueryExecutor,
                            scope: List<PolySymbolsScope>,
                            properties: Map<String, Any>): List<PolySymbolCodeCompletionItem>

  fun filterNameMatches(matches: List<PolySymbol>,
                        queryExecutor: PolySymbolsQueryExecutor,
                        scope: List<PolySymbolsScope>,
                        properties: Map<String, Any>): List<PolySymbol>


  companion object {

    @JvmStatic
    fun get(name: String): WebSymbolsFilter = WebSymbolsFilterEP.get(name)

  }

}