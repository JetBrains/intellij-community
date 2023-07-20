// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.CssPart

class CustomElementsCssPartSymbol private constructor(
  name: String,
  cssPart: CssPart,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<CssPart>(name, cssPart, origin) {

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_CSS

  override val kind: SymbolKind
    get() = WebSymbol.KIND_CSS_PARTS

  companion object {
    fun create(cssPart: CssPart, origin: CustomElementsJsonOrigin): CustomElementsCssPartSymbol? {
      val name = cssPart.name ?: return null
      return CustomElementsCssPartSymbol(name, cssPart, origin)
    }
  }

}