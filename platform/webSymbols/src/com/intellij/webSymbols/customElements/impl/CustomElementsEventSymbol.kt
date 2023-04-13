// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.Event

class CustomElementsEventSymbol private constructor(
  name: String,
  event: Event,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Event>(name, event, origin) {

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_JS

  override val kind: SymbolKind
    get() = WebSymbol.KIND_JS_EVENTS

  companion object {
    fun create(event: Event, origin: CustomElementsJsonOrigin): CustomElementsEventSymbol? {
      val name = event.name ?: return null
      return CustomElementsEventSymbol(name, event, origin)
    }
  }

}