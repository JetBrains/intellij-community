// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.query.impl.WebSymbolNameConversionRulesImpl

interface WebSymbolNameConversionRules {

  val canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>
  val matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>
  val nameVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  companion object {

    @JvmStatic
    fun create(canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
               matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
               nameVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap()) =
      WebSymbolNameConversionRulesImpl(canonicalNames, matchNames, nameVariants)

    @JvmStatic
    fun empty() =
      WebSymbolNameConversionRulesImpl.empty

    @JvmStatic
    fun builder() =
      WebSymbolNameConversionRulesBuilder()

  }

}