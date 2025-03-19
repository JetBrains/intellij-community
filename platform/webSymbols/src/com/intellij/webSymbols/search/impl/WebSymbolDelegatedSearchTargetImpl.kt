// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search.impl

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.search.SearchRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.search.SearchScope
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolDelegate
import com.intellij.webSymbols.search.WebSymbolSearchTarget

/**
 * Used when creating a [WebSymbolSearchTarget] in [WebSymbolDelegate]. Allows to wrap a [SearchTarget] symbol
 * as a [WebSymbolSearchTarget], while exposing original behavior to the platform.
 */
internal class WebSymbolDelegatedSearchTargetImpl(override val symbol: WebSymbol) : WebSymbolSearchTarget {

  init {
    assert(symbol is SearchTarget)
  }

  override fun createPointer(): Pointer<out WebSymbolSearchTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolDelegatedSearchTargetImpl(it) }
    }
  }

  override fun presentation(): TargetPresentation =
    (symbol as SearchTarget).presentation()

  override val usageHandler: UsageHandler
    get() = (symbol as SearchTarget).usageHandler

  override val maximalSearchScope: SearchScope?
    get() = (symbol as SearchTarget).maximalSearchScope

  override val textSearchRequests: Collection<SearchRequest>
    get() = (symbol as SearchTarget).textSearchRequests

  override fun equals(other: Any?): Boolean =
    other === this ||
    (other is WebSymbolDelegatedSearchTargetImpl && other.symbol == symbol)

  override fun hashCode(): Int =
    symbol.hashCode()

}