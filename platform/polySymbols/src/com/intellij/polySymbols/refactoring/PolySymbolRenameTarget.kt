// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.refactoring.impl.PolySymbolRenameTargetImpl
import com.intellij.polySymbols.utils.acceptSymbolForPsiSourcedPolySymbolRenameHandler
import com.intellij.refactoring.rename.api.RenameTarget

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