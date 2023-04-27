// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.*
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.json.CustomElementExport
import com.intellij.webSymbols.customElements.json.createPattern
import com.intellij.webSymbols.customElements.json.toApiStatus
import com.intellij.webSymbols.impl.StaticWebSymbolsScopeBase
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class CustomElementsCustomElementExportSymbol private constructor(
  override val name: String,
  override val origin: WebSymbolOrigin,
  override val pattern: WebSymbolsPattern,
  override val apiStatus: WebSymbolApiStatus,
) : CustomElementsSymbol, StaticWebSymbolsScopeBase.StaticSymbolContributionAdapter {
  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_HTML
  override val kind: SymbolKind
    get() = WebSymbol.KIND_HTML_ELEMENTS
  override val framework: FrameworkId?
    get() = null

  override fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): WebSymbol =
    this

  override fun createPointer(): Pointer<out WebSymbol> =
    Pointer.hardPointer(this)

  companion object {
    fun create(export: CustomElementExport, origin: CustomElementsJsonOrigin): CustomElementsCustomElementExportSymbol? {
      val name = export.name ?: return null
      val referencePattern = export.declaration?.createPattern(origin) ?: return null
      return CustomElementsCustomElementExportSymbol(name, origin, referencePattern,
                                                     export.deprecated.toApiStatus(origin) ?: WebSymbolApiStatus.Stable)
    }
  }

}