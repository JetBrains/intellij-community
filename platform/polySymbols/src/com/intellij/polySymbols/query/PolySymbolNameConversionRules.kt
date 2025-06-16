// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.query.impl.PolySymbolNameConversionRulesImpl

interface PolySymbolNameConversionRules {

  /**
   * Used for storing and comparing symbols.
   *
   * @see [com.intellij.polySymbols.query.PolySymbolNamesProvider.Target.NAMES_MAP_STORAGE]
   */
  val canonicalNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  /**
   * Used for matching symbols.
   *
   * @see [com.intellij.polySymbols.query.PolySymbolNamesProvider.Target.NAMES_QUERY]
   */
  val matchNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  /**
   * Used for renaming symbols.
   *
   * @see [com.intellij.polySymbols.query.PolySymbolNamesProvider.Target.RENAME_QUERY]
   */
  val renames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  /**
   * Used for code completion.
   *
   * @see [com.intellij.polySymbols.query.PolySymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS]
   */
  val completionVariants: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  companion object {

    @JvmStatic
    fun create(
      canonicalNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter> = emptyMap(),
      matchNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter> = emptyMap(),
      completionVariants: Map<PolySymbolQualifiedKind, PolySymbolNameConverter> = emptyMap(),
      renames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter> = emptyMap(),
    ): PolySymbolNameConversionRules =
      PolySymbolNameConversionRulesImpl(canonicalNames, matchNames, completionVariants, renames)

    @JvmStatic
    fun create(symbolKind: PolySymbolQualifiedKind, converter: PolySymbolNameConverter): PolySymbolNameConversionRules =
      PolySymbolNameConversionRulesBuilder().addRule(symbolKind, converter).build()

    @JvmStatic
    fun create(vararg rules: Pair<PolySymbolQualifiedKind, PolySymbolNameConverter>): PolySymbolNameConversionRules =
      PolySymbolNameConversionRulesBuilder().apply { rules.forEach { rule -> addRule(rule.first, rule.second) } }.build()

    @JvmStatic
    fun empty(): PolySymbolNameConversionRules =
      PolySymbolNameConversionRulesImpl.EMPTY

    @JvmStatic
    fun builder(): PolySymbolNameConversionRulesBuilder =
      PolySymbolNameConversionRulesBuilder()

  }

}