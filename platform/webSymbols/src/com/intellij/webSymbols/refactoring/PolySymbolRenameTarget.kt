// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.refactoring.impl.PolySymbolRenameTargetImpl

interface PolySymbolRenameTarget : RenameTarget {

  val symbol: PolySymbol

  override fun createPointer(): Pointer<out PolySymbolRenameTarget>

  companion object {
    fun create(symbol: PolySymbol): PolySymbolRenameTarget? =
      if (!acceptSymbolForPsiSourcedWebSymbolRenameHandler(symbol))
        PolySymbolRenameTargetImpl(symbol)
      else
        null
  }

}