// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search.impl

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.search.SearchRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.utils.PolySymbolDelegate
import com.intellij.psi.search.SearchScope

/**
 * Used when creating a [PolySymbolSearchTarget] in [PolySymbolDelegate]. Allows to wrap a [SearchTarget] symbol
 * as a [PolySymbolSearchTarget], while exposing original behavior to the platform.
 */
internal class PolySymbolDelegatedSearchTargetImpl(override val symbol: PolySymbol) : PolySymbolSearchTarget {

  init {
    assert(symbol is SearchTarget)
  }

  override fun createPointer(): Pointer<out PolySymbolSearchTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { PolySymbolDelegatedSearchTargetImpl(it) }
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
    (other is PolySymbolDelegatedSearchTargetImpl && other.symbol == symbol)

  override fun hashCode(): Int =
    symbol.hashCode()

}