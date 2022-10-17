// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.declarations

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.webSymbols.WebSymbol

interface WebSymbolDeclaration : PsiSymbolDeclaration {

  override fun getSymbol(): WebSymbol

}