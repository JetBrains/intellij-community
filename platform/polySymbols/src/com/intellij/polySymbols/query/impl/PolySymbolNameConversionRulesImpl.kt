// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConverter

internal data class PolySymbolNameConversionRulesImpl(
  override val canonicalNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>,
  override val matchNames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>,
  override val completionVariants: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>,
  override val renames: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>,
) : PolySymbolNameConversionRules {
  companion object {
    val EMPTY = PolySymbolNameConversionRulesImpl(emptyMap(), emptyMap(), emptyMap(), emptyMap())
  }
}
