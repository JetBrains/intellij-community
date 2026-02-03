// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.webTypes.filters.PolySymbolFilter
import com.intellij.util.xmlb.annotations.Attribute

class PolySymbolFilterEP() : CustomLoadingExtensionPointBean<PolySymbolFilter>() {

  companion object {
    private val EP_NAME = ExtensionPointName<PolySymbolFilterEP>("com.intellij.polySymbols.webTypes.filter")

    fun get(name: String): PolySymbolFilter =
      EP_NAME.getByKey(name, PolySymbol::class.java) { it.name }?.instance
      ?: NOOP_FILTER

    private val NOOP_FILTER = object : PolySymbolFilter {
      override fun filterCodeCompletions(
        codeCompletions: List<PolySymbolCodeCompletionItem>,
        queryExecutor: PolySymbolQueryExecutor,
        stack: PolySymbolQueryStack,
        properties: Map<String, Any>,
      ): List<PolySymbolCodeCompletionItem> =
        codeCompletions

      override fun filterNameMatches(
        matches: List<PolySymbol>,
        queryExecutor: PolySymbolQueryExecutor,
        stack: PolySymbolQueryStack,
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