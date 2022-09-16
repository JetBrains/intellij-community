package com.intellij.javascript.web.symbols

data class WebSymbolsNameMatchQueryParams(override val registry: WebSymbolsRegistry,
                                          override val virtualSymbols: Boolean = true,
                                          val abstractSymbols: Boolean = false,
                                          val strictScope: Boolean = false) : WebSymbolsRegistryQueryParams