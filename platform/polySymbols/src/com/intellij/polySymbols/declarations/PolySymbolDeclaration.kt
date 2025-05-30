// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.declarations

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.polySymbols.PolySymbol

interface PolySymbolDeclaration : PsiSymbolDeclaration {

  override fun getSymbol(): PolySymbol

}