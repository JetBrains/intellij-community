// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem

interface PolySymbolsQueryResultsCustomizer : ModificationTracker {

  fun createPointer(): Pointer<out PolySymbolsQueryResultsCustomizer>

  fun apply(matches: List<PolySymbol>,
            strict: Boolean,
            qualifiedName: PolySymbolQualifiedName): List<PolySymbol>

  fun apply(item: PolySymbolCodeCompletionItem,
            qualifiedKind: PolySymbolQualifiedKind): PolySymbolCodeCompletionItem?

}