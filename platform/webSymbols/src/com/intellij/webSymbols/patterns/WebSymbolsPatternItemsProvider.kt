// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.WebSymbolsRegistry

interface WebSymbolsPatternItemsProvider {
  @JvmDefault
  fun getSymbolTypes(context: WebSymbol?): Set<WebSymbol.SymbolType> =
    emptySet()

  val delegate: WebSymbol?

  fun codeCompletion(name: String,
                     position: Int,
                     contextStack: Stack<WebSymbolsContainer>,
                     registry: WebSymbolsRegistry): List<WebSymbolCodeCompletionItem>

  fun matchName(name: String,
                contextStack: Stack<WebSymbolsContainer>,
                registry: WebSymbolsRegistry): List<WebSymbol>

}