// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search.impl

import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.search.PolySymbolSearchTarget

internal class PolySymbolSearchTargetImpl(override val symbol: PolySymbol) : PolySymbolSearchTarget {

  override fun createPointer(): Pointer<out PolySymbolSearchTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { PolySymbolSearchTargetImpl(it) }
    }
  }

  override fun presentation(): TargetPresentation =
    symbol.presentation

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(presentation().presentableText)

  override fun equals(other: Any?): Boolean =
    other === this ||
    (other is PolySymbolSearchTargetImpl && other.symbol == symbol)

  override fun hashCode(): Int =
    symbol.hashCode()

}