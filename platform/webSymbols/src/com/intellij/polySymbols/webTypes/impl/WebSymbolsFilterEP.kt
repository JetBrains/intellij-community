// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.webTypes.filters.WebSymbolsFilter

class WebSymbolsFilterEP() : CustomLoadingExtensionPointBean<WebSymbolsFilter>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebSymbolsFilterEP>("com.intellij.webSymbols.webTypes.filter")

    fun get(name: String): WebSymbolsFilter =
      EP_NAME.getByKey(name, PolySymbol::class.java) { it.name }?.instance
      ?: NOOP_FILTER

    private val NOOP_FILTER = object : WebSymbolsFilter {
      override fun filterCodeCompletions(
        codeCompletions: List<PolySymbolCodeCompletionItem>,
        queryExecutor: PolySymbolsQueryExecutor,
        scope: List<PolySymbolsScope>,
        properties: Map<String, Any>,
      ): List<PolySymbolCodeCompletionItem> =
        codeCompletions

      override fun filterNameMatches(
        matches: List<PolySymbol>,
        queryExecutor: PolySymbolsQueryExecutor,
        scope: List<PolySymbolsScope>,
        properties: Map<String, Any>,
      ): List<PolySymbol> =
        matches

    }
  }

  @Attribute("name")
  @JvmField
  var name: String? = null

  @Attribute("implementation")
  @JvmField
  var implementation: String? = null

  override fun getImplementationClassName(): String? =
    implementation
}