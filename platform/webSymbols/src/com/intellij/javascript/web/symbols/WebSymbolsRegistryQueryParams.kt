package com.intellij.javascript.web.symbols

interface WebSymbolsRegistryQueryParams {

  val framework: String?
    get() = registry.framework

  val registry: WebSymbolsRegistry

  val virtualSymbols: Boolean
}