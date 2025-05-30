// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.SymbolKind
import com.intellij.polySymbols.SymbolNamespace
import com.intellij.polySymbols.PolySymbol.Companion.KIND_CSS_PROPERTIES
import com.intellij.polySymbols.PolySymbol.Companion.NAMESPACE_CSS
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.CssCustomProperty

class CustomElementsCssCustomPropertySymbol private constructor(
  name: String,
  cssCustomProperty: CssCustomProperty,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<CssCustomProperty>(name, cssCustomProperty, origin) {

  override val namespace: SymbolNamespace
    get() = NAMESPACE_CSS

  override val kind: SymbolKind
    get() = KIND_CSS_PROPERTIES

  companion object {
    fun create(cssCustomProperty: CssCustomProperty, origin: CustomElementsJsonOrigin): CustomElementsCssCustomPropertySymbol? {
      val name = cssCustomProperty.name ?: return null
      return CustomElementsCssCustomPropertySymbol(name, cssCustomProperty, origin)
    }
  }

}