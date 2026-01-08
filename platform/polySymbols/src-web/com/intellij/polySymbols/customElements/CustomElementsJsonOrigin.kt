// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.customElements.json.SourceReference
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@Internal
interface CustomElementsJsonOrigin : PolySymbolOrigin {
  val library: String

  val version: String?

  val typeSupport: PolySymbolTypeSupport?

  fun resolveSourceSymbol(source: SourceReference, cacheHolder: UserDataHolderEx): PsiElement?

  fun renderDescription(description: String): @NlsSafe String

}