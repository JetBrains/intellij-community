// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.WebSymbolQualifiedKind

class WebSymbolNameConversionRulesBuilder internal constructor() {

  private val canonicalNames = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
  private val matchNames = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
  private val renameRules = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
  private val completionVariants = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()

  fun addCanonicalNamesRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    canonicalNames.putIfAbsent(symbolKind, converter)
  }

  fun addMatchNamesRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    matchNames.putIfAbsent(symbolKind, converter)
  }

  fun addRenameRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    renameRules.putIfAbsent(symbolKind, converter)
  }

  fun addCompletionVariantsRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
    completionVariants.putIfAbsent(symbolKind, converter)
  }

  fun addRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRulesBuilder = apply {
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