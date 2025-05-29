// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.filters

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.query.PolySymbolsQueryExecutor
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP

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