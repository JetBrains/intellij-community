// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.webTypes.filters.WebSymbolsFilter
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class WebSymbolsFilterEP internal constructor() : CustomLoadingExtensionPointBean<WebSymbolsFilter>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebSymbolsFilterEP>("com.intellij.webSymbols.webTypes.filter")

    fun get(name: String): WebSymbolsFilter =
      EP_NAME.getByKey(name, WebSymbol::class.java) { it.name }?.instance
      ?: NOOP_FILTER

    private val NOOP_FILTER = object : WebSymbolsFilter {
      override fun filterCodeCompletions(codeCompletions: List<WebSymbolCodeCompletionItem>,
                                         queryExecutor: WebSymbolsQueryExecutor,
                                         scope: List<WebSymbolsScope>,
                                         properties: Map<String, Any>): List<WebSymbolCodeCompletionItem> =
        codeCompletions

      override fun filterNameMatches(matches: List<WebSymbol>,
                                     queryExecutor: WebSymbolsQueryExecutor,
                                     scope: List<WebSymbolsScope>,
                                     properties: Map<String, Any>): List<WebSymbol> =
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