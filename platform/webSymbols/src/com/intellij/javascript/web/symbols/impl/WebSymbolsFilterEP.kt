package com.intellij.javascript.web.symbols.impl

import com.intellij.javascript.web.symbols.*
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

internal class WebSymbolsFilterEP : CustomLoadingExtensionPointBean<WebSymbolsFilter>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebSymbolsFilterEP>("com.intellij.javascript.web.filter")

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