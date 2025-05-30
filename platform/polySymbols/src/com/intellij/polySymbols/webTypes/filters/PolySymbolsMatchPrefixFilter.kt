// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.filters

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolsMatchPrefixFilter : PolySymbolsFilter {

  override fun filterCodeCompletions(codeCompletions: List<PolySymbolCodeCompletionItem>,
                                     queryExecutor: PolySymbolsQueryExecutor,
                                     scope: List<PolySymbolsScope>,
                                     properties: Map<String, Any>): List<PolySymbolCodeCompletionItem> {
    val prefix = properties["prefix"] as? String ?: return codeCompletions
    return codeCompletions.filter { it.name.startsWith(prefix) }
  }

  override fun filterNameMatches(matches: List<PolySymbol>,
                                 queryExecutor: PolySymbolsQueryExecutor,
                                 scope: List<PolySymbolsScope>,
                                 properties: Map<String, Any>): List<PolySymbol> {
    val prefix = properties["prefix"] as? String ?: return matches
    return matches.filter { it.name.startsWith(prefix) }
  }

}