// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.Slot

class CustomElementsSlotSymbol private constructor(
  name: String,
  slot: Slot,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Slot>(name, slot, origin) {

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_HTML

  override val kind: SymbolKind
    get() = WebSymbol.KIND_HTML_SLOTS

  companion object {
    fun create(slot: Slot, origin: CustomElementsJsonOrigin): CustomElementsSlotSymbol? {
      val name = slot.name ?: return null
      return CustomElementsSlotSymbol(name, slot, origin)
    }
  }

}