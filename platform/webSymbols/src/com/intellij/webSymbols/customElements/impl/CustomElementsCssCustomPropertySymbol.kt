// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PROPERTIES
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_CSS
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.CssCustomProperty

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