// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem

interface WebSymbolsScope : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsScope>

  fun apply(matches: List<WebSymbol>,
            strict: Boolean,
            namespace: SymbolNamespace?,
            kind: SymbolKind,
            name: String?): List<WebSymbol>

  fun apply(item: WebSymbolCodeCompletionItem,
            namespace: SymbolNamespace?,
            kind: SymbolKind): WebSymbolCodeCompletionItem?

  companion object {
    @JvmStatic
    fun List<WebSymbol>.applyScope(scope: WebSymbolsScope,
                                   strict: Boolean,
                                   namespace: SymbolNamespace?,
                                   kind: SymbolKind,
                                   name: String?): List<WebSymbol> =
      scope.apply(this, strict, namespace, kind, name)

    @JvmStatic
    fun WebSymbolCodeCompletionItem.applyScope(scope: WebSymbolsScope,
                                               namespace: SymbolNamespace?,
                                               kind: SymbolKind): WebSymbolCodeCompletionItem? =
      scope.apply(this, namespace, kind)
  }

}