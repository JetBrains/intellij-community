// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem

interface WebSymbolsQueryResultsCustomizer : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsQueryResultsCustomizer>

  fun apply(matches: List<PolySymbol>,
            strict: Boolean,
            qualifiedName: PolySymbolQualifiedName): List<PolySymbol>

  fun apply(item: WebSymbolCodeCompletionItem,
            qualifiedKind: PolySymbolQualifiedKind): WebSymbolCodeCompletionItem?

}