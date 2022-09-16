package com.intellij.javascript.web.symbols

data class WebSymbolsCodeCompletionQueryParams(
  override val registry: WebSymbolsRegistry,
  /** Position to complete at in the last segment of the path **/
  val position: Int,
  override val virtualSymbols: Boolean = true,
) : WebSymbolsRegistryQueryParams