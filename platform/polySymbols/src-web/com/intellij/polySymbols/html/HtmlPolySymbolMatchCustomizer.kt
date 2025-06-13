// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolMatchCustomizer
import com.intellij.polySymbols.query.PolySymbolMatchCustomizerFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object HtmlPolySymbolMatchCustomizer : PolySymbolMatchCustomizer {

  override fun mergeModifiers(current: Set<PolySymbolModifier>?, toMerge: Set<PolySymbolModifier>, symbol: PolySymbol): Set<PolySymbolModifier>? {
    return setOfNotNull(
      PolySymbolModifier.VIRTUAL.takeIf { (current != null && PolySymbolModifier.VIRTUAL in current) || PolySymbolModifier.VIRTUAL in toMerge },
    )
  }

  class Factory : PolySymbolMatchCustomizerFactory {
    override fun create(symbol: PolySymbolMatch): PolySymbolMatchCustomizer? =
      if (symbol.qualifiedKind.namespace == NAMESPACE_HTML)
        HtmlPolySymbolMatchCustomizer
      else
        null
  }
}