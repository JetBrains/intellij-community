// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.utils.asSingleSymbol
import com.intellij.webSymbols.utils.nameMatches
import com.intellij.webSymbols.utils.qualifiedName

class SingleSymbolReferencePattern(private val path: List<WebSymbolQualifiedName>,
                                   private val virtualSymbols: Boolean = true,
                                   private val abstractSymbols: Boolean = false) : WebSymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> =
    emptySequence()

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    if (owner?.nameMatches(params.name.substring(start, end), params.queryExecutor) == true)
      params.queryExecutor.runNameMatchQuery(path, virtualSymbols, abstractSymbols, false, scopeStack.toList())
        .asSingleSymbol()
        ?.let { listOf(MatchResult(WebSymbolNameSegment(start, end, it))) }
      ?: emptyList()
    else
      emptyList()

  override fun list(owner: WebSymbol?,
                    scopeStack: Stack<WebSymbolsScope>,
                    symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                    params: ListParameters): List<ListResult> =
    if (owner != null) {
      params.queryExecutor.runNameMatchQuery(path, virtualSymbols, abstractSymbols, false, scopeStack.toList())
        .asSingleSymbol()
        ?.let { listOf(ListResult(owner.name, WebSymbolNameSegment(0, owner.name.length, it))) }
      ?: emptyList()
    }
    else emptyList()

  override fun complete(owner: WebSymbol?,
                        scopeStack: Stack<WebSymbolsScope>,
                        symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                        params: CompletionParameters,
                        start: Int,
                        end: Int): CompletionResults =
    if (owner != null
        && params.queryExecutor.runNameMatchQuery(path, virtualSymbols, abstractSymbols, false, scopeStack.toList()).isNotEmpty()) {
      CompletionResults(params.queryExecutor.namesProvider
                          .getNames(owner.qualifiedName, WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
                          .map { WebSymbolCodeCompletionItem.create(it, 0, symbol = owner) })
    }
    else {
      CompletionResults(emptyList())
    }
}