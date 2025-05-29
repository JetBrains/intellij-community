// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.json.CustomElementExport
import com.intellij.webSymbols.customElements.json.createPattern
import com.intellij.webSymbols.customElements.json.toApiStatus
import com.intellij.webSymbols.impl.StaticPolySymbolsScopeBase
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class CustomElementsCustomElementExportSymbol private constructor(
  override val name: String,
  override val origin: PolySymbolOrigin,
  override val pattern: PolySymbolsPattern,
  override val apiStatus: PolySymbolApiStatus,
) : CustomElementsSymbol, StaticPolySymbolsScopeBase.StaticSymbolContributionAdapter {
  override val namespace: SymbolNamespace
    get() = PolySymbol.NAMESPACE_HTML
  override val kind: SymbolKind
    get() = PolySymbol.KIND_HTML_ELEMENTS
  override val framework: FrameworkId?
    get() = null

  override fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): PolySymbol =
    this

  override fun createPointer(): Pointer<out PolySymbol> =
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