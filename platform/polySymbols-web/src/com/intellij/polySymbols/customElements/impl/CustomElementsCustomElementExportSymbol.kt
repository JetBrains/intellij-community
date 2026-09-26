// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.json.CustomElementExport
import com.intellij.polySymbols.customElements.json.createPattern
import com.intellij.polySymbols.customElements.json.toApiStatus
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolWithPattern

class CustomElementsCustomElementExportSymbol private constructor(
  override val name: String,
  override val origin: CustomElementsJsonOrigin,
  override val pattern: PolySymbolPattern,
  override val apiStatus: PolySymbolApiStatus,
) : CustomElementsSymbol, PolySymbolWithPattern, StaticPolySymbolScopeBase.StaticSymbolContributionAdapter {

  override val kind: PolySymbolKind
    get() = HTML_ELEMENTS

  override val framework: FrameworkId?
    get() = null

  override fun withQueryExecutorContext(queryExecutor: PolySymbolQueryExecutor): PolySymbol =
    this

  override fun createPointer(): Pointer<out CustomElementsSymbol> =
    Pointer.hardPointer(this)

  override fun matchContext(context: PolyContext): Boolean =
    super<CustomElementsSymbol>.matchContext(context)

  companion object {
    fun create(export: CustomElementExport, origin: CustomElementsJsonOrigin): CustomElementsCustomElementExportSymbol? {
      val name = export.name ?: return null
      val referencePattern = export.declaration?.createPattern(origin) ?: return null
      return CustomElementsCustomElementExportSymbol(name, origin, referencePattern,
                                                     export.deprecated.toApiStatus(origin) ?: PolySymbolApiStatus.Stable)
    }
  }

}