// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.impl.toCodeCompletionItems
import java.util.*
import javax.swing.Icon

/*
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("DEPRECATION")
interface WebSymbolsContainer : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsContainer>

  @JvmDefault
  fun getSymbols(namespace: SymbolNamespace?,
                 kind: SymbolKind,
                 name: String?,
                 params: WebSymbolsNameMatchQueryParams,
                 context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    emptyList()

  @JvmDefault
  fun getCodeCompletions(namespace: SymbolNamespace?,
                         kind: SymbolKind,
                         name: String?,
                         params: WebSymbolsCodeCompletionQueryParams,
                         context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    getSymbols(namespace, kind, null, WebSymbolsNameMatchQueryParams(params.registry), context)
      .flatMap { (it as? WebSymbol)?.toCodeCompletionItems(name, params, context) ?: emptyList() }

  @JvmDefault
  fun isExclusiveFor(namespace: SymbolNamespace?, kind: SymbolKind): Boolean =
    false

  interface Origin {
    @JvmDefault
    val framework: FrameworkId?
      get() = null

    @JvmDefault
    val library: String?
      get() = null

    @JvmDefault
    val version: String?
      get() = null

    @JvmDefault
    val defaultIcon: Icon?
      get() = null
  }

  data class OriginData(override val framework: FrameworkId? = null,
                        override val library: String? = null,
                        override val version: String? = null,
                        override val defaultIcon: Icon? = null) : Origin

  @Suppress("MayBeConstant")
  companion object {
    @JvmField
    val NAMESPACE_HTML = "html"
    @JvmField
    val NAMESPACE_CSS = "css"
    @JvmField
    val NAMESPACE_JS = "js"

    @JvmField
    val EMPTY_ORIGIN: Origin = OriginData()
  }

}