// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.PolySymbolQualifiedKind

class WebSymbolNameConversionRulesBuilder internal constructor() {

  private val canonicalNames = mutableMapOf<PolySymbolQualifiedKind, WebSymbolNameConverter>()
  private val matchNames = mutableMapOf<PolySymbolQualifiedKind, WebSymbolNameConverter>()
  private val renameRules = mutableMapOf<PolySymbolQualifiedKind, WebSymbolNameConverter>()
  private val completionVariants = mutableMapOf<PolySymbolQualifiedKind, WebSymbolNameConverter>()

  fun addCanonicalNamesRule(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    canonicalNames.putIfAbsent(symbolKind, converter)
  }

  fun addMatchNamesRule(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    matchNames.putIfAbsent(symbolKind, converter)
  }

  fun addRenameRule(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    renameRules.putIfAbsent(symbolKind, converter)
  }

  fun addCompletionVariantsRule(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    completionVariants.putIfAbsent(symbolKind, converter)
  }

  fun addRule(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    addCanonicalNamesRule(symbolKind, converter)
    addMatchNamesRule(symbolKind, converter)
    addRenameRule(symbolKind, converter)
    addCompletionVariantsRule(symbolKind, converter)
  }

  fun build(): WebSymbolNameConversionRules =
    WebSymbolNameConversionRules.create(canonicalNames.toMap(), matchNames.toMap(), completionVariants.toMap(), renameRules.toMap())

  fun isEmpty(): Boolean =
    canonicalNames.isEmpty() && matchNames.isEmpty() && completionVariants.isEmpty()

}