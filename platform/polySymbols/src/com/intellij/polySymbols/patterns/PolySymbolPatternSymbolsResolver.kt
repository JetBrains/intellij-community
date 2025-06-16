// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PolySymbolPatternSymbolsResolver {
  fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
    emptySet()

  val delegate: PolySymbol?

  fun codeCompletion(
    name: String,
    position: Int,
    stack: PolySymbolQueryStack,
    queryExecutor: PolySymbolQueryExecutor,
  ): List<PolySymbolCodeCompletionItem>

  fun listSymbols(
    stack: PolySymbolQueryStack,
    queryExecutor: PolySymbolQueryExecutor,
    expandPatterns: Boolean,
  ): List<PolySymbol>

  fun matchName(
    name: String,
    stack: PolySymbolQueryStack,
    queryExecutor: PolySymbolQueryExecutor,
  ): List<PolySymbol>

}