// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.WebSymbolQualifiedKind

class WebSymbolNameConversionRulesBuilder internal constructor() {

  private val canonicalNames = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
  private val matchNames = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
  private val nameVariants = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()

  fun addCanonicalNamesRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter) {
    canonicalNames.putIfAbsent(symbolKind, converter)
  }

  fun addMatchNamesRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter) {
    matchNames.putIfAbsent(symbolKind, converter)
  }

  fun addNameVariantsRule(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter) {
    nameVariants.putIfAbsent(symbolKind, converter)
  }

  fun build(): WebSymbolNameConversionRules =
    WebSymbolNameConversionRules.create(canonicalNames.toMap(), matchNames.toMap(), nameVariants.toMap())

  fun isEmpty(): Boolean =
    canonicalNames.isEmpty() && matchNames.isEmpty() && nameVariants.isEmpty()

}