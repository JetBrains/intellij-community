// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.refactoring.WebSymbolRenameTarget

internal class WebSymbolRenameTargetImpl(override val symbol: WebSymbol) : WebSymbolRenameTarget {

  override fun createPointer(): Pointer<out WebSymbolRenameTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolRenameTargetImpl(it) }
    }
  }

  override val targetName: String
    get() = symbol.name

  override fun presentation(): TargetPresentation =
    symbol.presentation

  override fun equals(other: Any?): Boolean =
    other is WebSymbolRenameTargetImpl && other.symbol == symbol

  override fun hashCode(): Int =
    symbol.hashCode()
}