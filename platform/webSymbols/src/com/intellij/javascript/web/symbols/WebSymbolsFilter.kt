package com.intellij.javascript.web.symbols

import com.intellij.javascript.web.symbols.impl.WebSymbolsFilterEP
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
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