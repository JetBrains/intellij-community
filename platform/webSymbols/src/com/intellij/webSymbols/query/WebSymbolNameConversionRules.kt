// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.query.impl.WebSymbolNameConversionRulesImpl

interface WebSymbolNameConversionRules {

  /**
   * Used for storing and comparing symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE]
   */
  val canonicalNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for matching symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.NAMES_QUERY]
   */
  val matchNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for renaming symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.RENAME_QUERY]
   */
  val renames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for code completion.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS]
   */
  val completionVariants: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>

  companion object {

    @JvmStatic
    fun create(
      canonicalNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      matchNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      completionVariants: Map<PolySymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      renames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
    ): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesImpl(canonicalNames, matchNames, completionVariants, renames)

    @JvmStatic
    fun create(symbolKind: PolySymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesBuilder().addRule(symbolKind, converter).build()

    @JvmStatic
    fun create(vararg rules: Pair<PolySymbolQualifiedKind, WebSymbolNameConverter>): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesBuilder().apply { rules.forEach { rule -> addRule(rule.first, rule.second) } }.build()

    @JvmStatic
    fun empty(): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesImpl.EMPTY

    @JvmStatic
    fun builder(): WebSymbolNameConversionRulesBuilder =
      WebSymbolNameConversionRulesBuilder()

  }

}