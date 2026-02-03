// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.refactoring.impl.PolySymbolRenameTargetImpl
import com.intellij.polySymbols.utils.acceptSymbolForPsiSourcedPolySymbolRenameHandler
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsageSearcher

/**
 * A specialized [RenameTarget], which provides the [PolySymbol],
 * which is being renamed for the Poly Symbol framework. It allows
 * running the rename through generic [RenameUsageSearcher] and handling
 * various edge cases through framework APIs.
 */
interface PolySymbolRenameTarget : RenameTarget {

  val symbol: PolySymbol

  override fun createPointer(): Pointer<out PolySymbolRenameTarget>

  companion object {
    fun create(symbol: PolySymbol): PolySymbolRenameTarget? =
      if (!acceptSymbolForPsiSourcedPolySymbolRenameHandler(symbol))
        PolySymbolRenameTargetImpl(symbol)
      else
        null
  }

}