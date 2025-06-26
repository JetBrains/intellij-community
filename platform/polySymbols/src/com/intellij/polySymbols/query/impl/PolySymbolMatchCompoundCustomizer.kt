// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolMatchCustomizer

class PolySymbolMatchCompoundCustomizer(private val list: List<PolySymbolMatchCustomizer>) : PolySymbolMatchCustomizer {

  override fun mergeModifiers(
    current: Set<PolySymbolModifier>?,
    toMerge: Set<PolySymbolModifier>,
    symbol: PolySymbol,
  ): Set<PolySymbolModifier>? =
    list.firstNotNullOfOrNull { it.mergeModifiers(current, toMerge, symbol) }

}