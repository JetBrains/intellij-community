// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.SymbolKind
import com.intellij.polySymbols.SymbolNamespace
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.CssPart

class CustomElementsCssPartSymbol private constructor(
  name: String,
  cssPart: CssPart,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<CssPart>(name, cssPart, origin) {

  override val namespace: SymbolNamespace
    get() = PolySymbol.NAMESPACE_CSS

  override val kind: SymbolKind
    get() = PolySymbol.KIND_CSS_PARTS

  companion object {
    fun create(cssPart: CssPart, origin: CustomElementsJsonOrigin): CustomElementsCssPartSymbol? {
      val name = cssPart.name ?: return null
      return CustomElementsCssPartSymbol(name, cssPart, origin)
    }
  }

}