// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.impl.toCodeCompletionItems
import java.util.*

/*
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("DEPRECATION")
interface WebSymbolsContainer : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsContainer>

  @JvmDefault
  fun getSymbols(namespace: SymbolNamespace?,
                 kind: SymbolKind,
                 name: String?,
                 params: WebSymbolsNameMatchQueryParams,
                 context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    emptyList()

  @JvmDefault
  fun getCodeCompletions(namespace: SymbolNamespace?,
                         kind: SymbolKind,
                         name: String?,
                         params: WebSymbolsCodeCompletionQueryParams,
                         context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    getSymbols(namespace, kind, null, WebSymbolsNameMatchQueryParams(params.registry), context)
      .flatMap { (it as? WebSymbol)?.toCodeCompletionItems(name, params, context) ?: emptyList() }

  @JvmDefault
  fun isExclusiveFor(namespace: SymbolNamespace?, kind: SymbolKind): Boolean =
    false

}