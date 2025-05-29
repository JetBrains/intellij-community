// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.SymbolKind
import com.intellij.polySymbols.SymbolNamespace
import com.intellij.polySymbols.PolySymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.polySymbols.PolySymbol.Companion.NAMESPACE_HTML
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.Attribute
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue

class CustomElementsAttributeSymbol private constructor(
  name: String,
  attribute: Attribute,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Attribute>(name, attribute, origin) {

  override val namespace: SymbolNamespace
    get() = NAMESPACE_HTML

  override val kind: SymbolKind
    get() = KIND_HTML_ATTRIBUTES

  override val defaultValue: String?
    get() = contribution.default

  override val attributeValue: PolySymbolHtmlAttributeValue?
    get() = PolySymbolHtmlAttributeValue.create(
      type = if (type != null) PolySymbolHtmlAttributeValue.Type.OF_MATCH else null,
      default = contribution.default,
    ).takeIf { it.type != null || it.default != null }

  companion object {
    fun create(attribute: Attribute, origin: CustomElementsJsonOrigin): CustomElementsAttributeSymbol? {
      val name = attribute.name ?: return null
      return CustomElementsAttributeSymbol(name, attribute, origin)
    }
  }

}