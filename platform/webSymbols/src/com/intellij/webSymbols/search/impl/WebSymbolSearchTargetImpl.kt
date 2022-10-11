// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search.impl

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.search.WebSymbolSearchTarget

class WebSymbolSearchTargetImpl(override val symbol: WebSymbol) : WebSymbolSearchTarget {

  override fun createPointer(): Pointer<out SearchTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolSearchTargetImpl(it) }
    }
  }

  override fun presentation(): TargetPresentation {
    return symbol.presentation
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(presentation().presentableText)

  override fun equals(other: Any?): Boolean =
    other === this ||
    (other is WebSymbolSearchTargetImpl && other.symbol == symbol)

  override fun hashCode(): Int =
    symbol.hashCode()

}