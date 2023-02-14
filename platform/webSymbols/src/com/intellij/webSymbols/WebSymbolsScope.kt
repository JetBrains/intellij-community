// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.impl.toCodeCompletionItems
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams

interface WebSymbolsScope : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsScope>

  fun getSymbols(namespace: SymbolNamespace,
                 kind: SymbolKind,
                 name: String?,
                 params: WebSymbolsNameMatchQueryParams,
                 scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    emptyList()

  fun getCodeCompletions(namespace: SymbolNamespace,
                         kind: SymbolKind,
                         name: String?,
                         params: WebSymbolsCodeCompletionQueryParams,
                         scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    getSymbols(namespace, kind, null, WebSymbolsNameMatchQueryParams(params.queryExecutor), scope)
      .flatMap { (it as? WebSymbol)?.toCodeCompletionItems(name, params, scope) ?: emptyList() }

  fun isExclusiveFor(namespace: SymbolNamespace, kind: SymbolKind): Boolean =
    false

}