// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.query.impl.WebSymbolNameConversionRulesImpl

interface WebSymbolNameConversionRules {

  /**
   * Used for storing and comparing symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE]
   */
  val canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for matching symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.NAMES_QUERY]
   */
  val matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for renaming symbols.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.RENAME_QUERY]
   */
  val renames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  /**
   * Used for code completion.
   *
   * @see [com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS]
   */
  val completionVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  companion object {

    @JvmStatic
    fun create(
      canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      completionVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
      renames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter> = emptyMap(),
    ): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesImpl(canonicalNames, matchNames, completionVariants, renames)

    @JvmStatic
    fun create(symbolKind: WebSymbolQualifiedKind, converter: WebSymbolNameConverter): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesBuilder().addRule(symbolKind, converter).build()

    @JvmStatic
    fun create(vararg rules: Pair<WebSymbolQualifiedKind, WebSymbolNameConverter>): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesBuilder().apply { rules.forEach { rule -> addRule(rule.first, rule.second) } }.build()

    @JvmStatic
    fun empty(): WebSymbolNameConversionRules =
      WebSymbolNameConversionRulesImpl.EMPTY

    @JvmStatic
    fun builder(): WebSymbolNameConversionRulesBuilder =
      WebSymbolNameConversionRulesBuilder()

  }

}