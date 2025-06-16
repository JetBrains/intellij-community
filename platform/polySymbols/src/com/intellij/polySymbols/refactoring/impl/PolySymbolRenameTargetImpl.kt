// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget

internal class PolySymbolRenameTargetImpl(override val symbol: PolySymbol) : PolySymbolRenameTarget {

  override fun createPointer(): Pointer<out PolySymbolRenameTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { PolySymbolRenameTargetImpl(it) }
    }
  }

  override val targetName: String
    get() = symbol.name

  override fun presentation(): TargetPresentation =
    symbol.presentation

  override fun equals(other: Any?): Boolean =
    other is PolySymbolRenameTargetImpl && other.symbol == symbol

  override fun hashCode(): Int =
    symbol.hashCode()
}