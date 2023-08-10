// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.customElements.json.SourceReference
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface CustomElementsJsonOrigin : WebSymbolOrigin {
  override val library: String

  fun resolveSourceSymbol(source: SourceReference, cacheHolder: UserDataHolderEx): PsiElement?

  fun renderDescription(description: String): @NlsSafe String

}