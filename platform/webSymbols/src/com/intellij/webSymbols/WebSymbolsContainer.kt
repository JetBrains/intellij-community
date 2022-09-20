// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.webSymbols.impl.toCodeCompletionItems
import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.Icon

@Suppress("DEPRECATION")
@ApiStatus.Experimental
interface WebSymbolsContainer : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsContainer>

  @JvmDefault
  fun getSymbols(namespace: Namespace?,
                 kind: SymbolKind,
                 name: String?,
                 params: WebSymbolsNameMatchQueryParams,
                 context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    emptyList()

  @JvmDefault
  fun getCodeCompletions(namespace: Namespace?,
                         kind: SymbolKind,
                         name: String?,
                         params: WebSymbolsCodeCompletionQueryParams,
                         context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    getSymbols(namespace, kind, null, WebSymbolsNameMatchQueryParams(params.registry), context)
      .flatMap { (it as? WebSymbol)?.toCodeCompletionItems(name, params, context) ?: emptyList() }

  @JvmDefault
  fun isExclusiveFor(namespace: Namespace?, kind: SymbolKind): Boolean =
    false

  interface Origin {
    @JvmDefault
    val framework: FrameworkId?
      get() = null

    @JvmDefault
    val packageName: String?
      get() = null

    @JvmDefault
    val version: String?
      get() = null

    @JvmDefault
    val defaultIcon: Icon?
      get() = null
  }

  data class OriginData(override val framework: FrameworkId? = null,
                        override val packageName: String? = null,
                        override val version: String? = null,
                        override val defaultIcon: Icon? = null) : Origin

  enum class Namespace {
    HTML,
    CSS,
    JS;

    companion object {
      @JvmStatic
      fun of(value: String): Namespace? =
        when (value.lowercase(Locale.US)) {
          NAMESPACE_HTML -> HTML
          NAMESPACE_CSS -> CSS
          NAMESPACE_JS -> JS
          else -> null
        }
    }
  }

  companion object {
    const val NAMESPACE_HTML = "html"
    const val NAMESPACE_CSS = "css"
    const val NAMESPACE_JS = "js"

    val EMPTY_ORIGIN: Origin = OriginData()
  }

}