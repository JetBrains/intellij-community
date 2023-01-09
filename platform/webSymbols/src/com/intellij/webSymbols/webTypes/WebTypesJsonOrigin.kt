// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.webTypes.json.SourceBase
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@Suppress("DEPRECATION")
@Internal
interface WebTypesJsonOrigin : WebSymbolOrigin {
  override val typeSupport: WebTypesSymbolTypeSupport?
    get() = null

  fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement?
  fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location?
  fun renderDescription(description: String): String?
  fun matchContext(context: WebSymbolsContext): Boolean
}