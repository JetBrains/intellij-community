// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.*

class WebSymbolsFilterEP internal constructor() : CustomLoadingExtensionPointBean<WebSymbolsFilter>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebSymbolsFilterEP>("com.intellij.webSymbols.filter")

    fun get(name: String): WebSymbolsFilter =
      EP_NAME.getByKey(name, WebSymbol::class.java) { it.name }?.instance
      ?: NOOP_FILTER

    private val NOOP_FILTER = object : WebSymbolsFilter {
      override fun filterCodeCompletions(codeCompletions: List<WebSymbolCodeCompletionItem>,
                                         registry: WebSymbolsRegistry,
                                         context: List<WebSymbolsContainer>,
                                         properties: Map<String, Any>): List<WebSymbolCodeCompletionItem> =
        codeCompletions

      override fun filterNameMatches(matches: List<WebSymbol>,
                                     registry: WebSymbolsRegistry,
                                     context: List<WebSymbolsContainer>,
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