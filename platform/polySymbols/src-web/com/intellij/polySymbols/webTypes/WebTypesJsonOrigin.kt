// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.webTypes.json.SourceBase
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface WebTypesJsonOrigin : PolySymbolOrigin {
  val framework: @NlsSafe FrameworkId?
    get() = null

  fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement?
  fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location?
  fun renderDescription(description: String): @NlsSafe String
  fun matchContext(context: PolyContext): Boolean
}