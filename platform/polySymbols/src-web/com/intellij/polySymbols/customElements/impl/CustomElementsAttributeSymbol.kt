// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.Attribute
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.PROP_HTML_ATTRIBUTE_VALUE
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue

class CustomElementsAttributeSymbol private constructor(
  name: String,
  attribute: Attribute,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Attribute>(name, attribute, origin) {

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = HTML_ATTRIBUTES

  override val defaultValue: String?
    get() = contribution.default

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      PROP_HTML_ATTRIBUTE_VALUE -> property.tryCast(
        PolySymbolHtmlAttributeValue.create(
          type = if (type != null) PolySymbolHtmlAttributeValue.Type.OF_MATCH else null,
          default = contribution.default,
        ).takeIf { it.type != null || it.default != null }
      )
      else -> super.get(property)
    }

  companion object {
    fun create(attribute: Attribute, origin: CustomElementsJsonOrigin): CustomElementsAttributeSymbol? {
      val name = attribute.name ?: return null
      return CustomElementsAttributeSymbol(name, attribute, origin)
    }
  }

}