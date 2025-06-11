// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.html.HTML_SLOTS
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.Slot

class CustomElementsSlotSymbol private constructor(
  name: String,
  slot: Slot,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Slot>(name, slot, origin) {

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = HTML_SLOTS

  companion object {
    fun create(slot: Slot, origin: CustomElementsJsonOrigin): CustomElementsSlotSymbol? {
      val name = slot.name ?: return null
      return CustomElementsSlotSymbol(name, slot, origin)
    }
  }

}