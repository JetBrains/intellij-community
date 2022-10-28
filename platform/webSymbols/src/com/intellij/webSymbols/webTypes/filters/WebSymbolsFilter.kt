// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.filters

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP

interface WebSymbolsFilter {

  fun filterCodeCompletions(codeCompletions: List<WebSymbolCodeCompletionItem>,
                            queryExecutor: WebSymbolsQueryExecutor,
                            scope: List<WebSymbolsScope>,
                            properties: Map<String, Any>): List<WebSymbolCodeCompletionItem>

  fun filterNameMatches(matches: List<WebSymbol>,
                        queryExecutor: WebSymbolsQueryExecutor,
                        scope: List<WebSymbolsScope>,
                        properties: Map<String, Any>): List<WebSymbol>


  companion object {

    @JvmStatic
    fun get(name: String): WebSymbolsFilter = WebSymbolsFilterEP.get(name)

  }

}