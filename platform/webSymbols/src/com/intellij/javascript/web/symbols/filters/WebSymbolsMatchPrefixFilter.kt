package com.intellij.javascript.web.symbols.filters

import com.intellij.javascript.web.symbols.*

class WebSymbolsMatchPrefixFilter: WebSymbolsFilter {

  override fun filterCodeCompletions(codeCompletions: List<WebSymbolCodeCompletionItem>,
                                     registry: WebSymbolsRegistry,
                                     context: List<WebSymbolsContainer>,
                                     properties: Map<String, Any>): List<WebSymbolCodeCompletionItem> {
    val prefix = properties["prefix"] as? String ?: return codeCompletions
    return codeCompletions.filter { it.name.startsWith(prefix) }
  }

  override fun filterNameMatches(matches: List<WebSymbol>,
                                 registry: WebSymbolsRegistry,
                                 context: List<WebSymbolsContainer>,
                                 properties: Map<String, Any>): List<WebSymbol> {
    val prefix = properties["prefix"] as? String ?: return matches
    return matches.filter { it.name.startsWith(prefix) }
  }

}