// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker

interface WebSymbolsScope : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsScope>

  fun apply(matches: List<WebSymbol>,
            strict: Boolean,
            namespace: WebSymbolsContainer.Namespace?,
            kind: SymbolKind,
            name: String?): List<WebSymbol>

  fun apply(item: WebSymbolCodeCompletionItem,
            namespace: WebSymbolsContainer.Namespace?,
            kind: SymbolKind): WebSymbolCodeCompletionItem?

  companion object {
    @JvmStatic
    fun List<WebSymbol>.applyScope(scope: WebSymbolsScope,
                                   strict: Boolean,
                                   namespace: WebSymbolsContainer.Namespace?,
                                   kind: SymbolKind,
                                   name: String?): List<WebSymbol> =
      scope.apply(this, strict, namespace, kind, name)

    @JvmStatic
    fun WebSymbolCodeCompletionItem.applyScope(scope: WebSymbolsScope,
                                               namespace: WebSymbolsContainer.Namespace?,
                                               kind: SymbolKind): WebSymbolCodeCompletionItem? =
      scope.apply(this, namespace, kind)
  }

}