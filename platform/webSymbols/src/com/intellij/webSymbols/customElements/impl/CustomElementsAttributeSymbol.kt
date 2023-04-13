// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.Attribute
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue

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

  override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = WebSymbolHtmlAttributeValue.create(
      type = if (type != null) WebSymbolHtmlAttributeValue.Type.OF_MATCH else null,
      default = contribution.default,
    ).takeIf { it.type != null || it.default != null }

  companion object {
    fun create(attribute: Attribute, origin: CustomElementsJsonOrigin): CustomElementsAttributeSymbol? {
      val name = attribute.name ?: return null
      return CustomElementsAttributeSymbol(name, attribute, origin)
    }
  }

}