// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbolKind

class PolySymbolNameConversionRulesBuilder internal constructor() {

  private val canonicalNames = mutableMapOf<PolySymbolKind, PolySymbolNameConverter>()
  private val matchNames = mutableMapOf<PolySymbolKind, PolySymbolNameConverter>()
  private val renameRules = mutableMapOf<PolySymbolKind, PolySymbolNameConverter>()
  private val completionVariants = mutableMapOf<PolySymbolKind, PolySymbolNameConverter>()

  fun addCanonicalNamesRule(symbolKind: PolySymbolKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRulesBuilder = apply {
    canonicalNames.putIfAbsent(symbolKind, converter)
  }

  fun addMatchNamesRule(symbolKind: PolySymbolKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRulesBuilder = apply {
    matchNames.putIfAbsent(symbolKind, converter)
  }

  fun addRenameRule(symbolKind: PolySymbolKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRulesBuilder = apply {
    renameRules.putIfAbsent(symbolKind, converter)
  }

  fun addCompletionVariantsRule(symbolKind: PolySymbolKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRulesBuilder =
    apply {
      completionVariants.putIfAbsent(symbolKind, converter)
    }

  fun addRule(symbolKind: PolySymbolKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRulesBuilder = apply {
    addCanonicalNamesRule(symbolKind, converter)
    addMatchNamesRule(symbolKind, converter)
    addRenameRule(symbolKind, converter)
    addCompletionVariantsRule(symbolKind, converter)
  }

  fun build(): PolySymbolNameConversionRules =
    PolySymbolNameConversionRules.create(canonicalNames.toMap(), matchNames.toMap(), completionVariants.toMap(), renameRules.toMap())

  fun isEmpty(): Boolean =
    canonicalNames.isEmpty() && matchNames.isEmpty() && completionVariants.isEmpty()

}