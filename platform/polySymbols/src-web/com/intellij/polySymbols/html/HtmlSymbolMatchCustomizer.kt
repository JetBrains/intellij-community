// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolMatchCustomizer
import com.intellij.polySymbols.query.PolySymbolMatchCustomizerFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object HtmlSymbolMatchCustomizer : PolySymbolMatchCustomizer {

  override fun mergeModifiers(current: Set<PolySymbolModifier>?, toMerge: Set<PolySymbolModifier>, symbol: PolySymbol): Set<PolySymbolModifier>? {
    val result = HashSet<PolySymbolModifier>()

    // If any of the matched symbols are virtual, the whole symbol match is also virtual
    if ((current != null && PolySymbolModifier.VIRTUAL in current) || PolySymbolModifier.VIRTUAL in toMerge)
      result.add(PolySymbolModifier.VIRTUAL)

    // The last of the matched symbols, which specifies either REQUIRED or OPTIONAL, takes precedence
    if (current != null && PolySymbolModifier.OPTIONAL in current)
      result.add(PolySymbolModifier.OPTIONAL)
    else if ((current != null && PolySymbolModifier.REQUIRED in current) || PolySymbolModifier.REQUIRED in toMerge)
      result.add(PolySymbolModifier.REQUIRED)
    else if (PolySymbolModifier.OPTIONAL in toMerge)
      result.add(PolySymbolModifier.OPTIONAL)

    return result
  }

  class Factory : PolySymbolMatchCustomizerFactory {
    override fun create(symbol: PolySymbolMatch): PolySymbolMatchCustomizer? =
      if (symbol.qualifiedKind.namespace == NAMESPACE_HTML)
        HtmlSymbolMatchCustomizer
      else
        null
  }
}