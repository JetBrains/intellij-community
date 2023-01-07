// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.filters

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.registry.WebSymbolsRegistry
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP

interface WebSymbolsFilter {

  fun filterCodeCompletions(codeCompletions: List<WebSymbolCodeCompletionItem>,
                            registry: WebSymbolsRegistry,
                            context: List<WebSymbolsContainer>,
                            properties: Map<String, Any>): List<WebSymbolCodeCompletionItem>

  fun filterNameMatches(matches: List<WebSymbol>,
                        registry: WebSymbolsRegistry,
                        context: List<WebSymbolsContainer>,
                        properties: Map<String, Any>): List<WebSymbol>


  companion object {

    @JvmStatic
    fun get(name: String): WebSymbolsFilter = WebSymbolsFilterEP.get(name)

  }

}