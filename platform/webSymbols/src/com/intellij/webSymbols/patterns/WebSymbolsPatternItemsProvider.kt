// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

interface WebSymbolsPatternItemsProvider {
  fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
    emptySet()

  val delegate: WebSymbol?

  fun codeCompletion(name: String,
                     position: Int,
                     scopeStack: Stack<WebSymbolsScope>,
                     queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolCodeCompletionItem>

  fun matchName(name: String,
                scopeStack: Stack<WebSymbolsScope>,
                queryExecutor: WebSymbolsQueryExecutor): List<WebSymbol>

}