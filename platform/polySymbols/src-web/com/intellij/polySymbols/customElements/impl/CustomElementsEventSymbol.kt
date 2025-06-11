// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.js.JS_EVENTS
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.Event

class CustomElementsEventSymbol private constructor(
  name: String,
  event: Event,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<Event>(name, event, origin) {

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = JS_EVENTS

  companion object {
    fun create(event: Event, origin: CustomElementsJsonOrigin): CustomElementsEventSymbol? {
      val name = event.name ?: return null
      return CustomElementsEventSymbol(name, event, origin)
    }
  }

}