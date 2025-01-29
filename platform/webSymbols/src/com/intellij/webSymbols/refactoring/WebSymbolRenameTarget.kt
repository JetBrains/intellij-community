// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntheticElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.refactoring.impl.WebSymbolRenameTargetImpl

interface WebSymbolRenameTarget : RenameTarget {

  val symbol: WebSymbol

  override fun createPointer(): Pointer<out WebSymbolRenameTarget>

  companion object {
    fun create(symbol: WebSymbol): WebSymbolRenameTarget? =
      if (!acceptSymbolForPsiSourcedWebSymbolRenameHandler(symbol))
        WebSymbolRenameTargetImpl(symbol)
      else
        null
  }

}