// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.query.PolySymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter

internal data class PolySymbolNameConversionRulesImpl(
  override val canonicalNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>,
  override val matchNames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>,
  override val completionVariants: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>,
  override val renames: Map<PolySymbolQualifiedKind, WebSymbolNameConverter>,
) : PolySymbolNameConversionRules {
  companion object {
    val EMPTY = PolySymbolNameConversionRulesImpl(emptyMap(), emptyMap(), emptyMap(), emptyMap())
  }
}
