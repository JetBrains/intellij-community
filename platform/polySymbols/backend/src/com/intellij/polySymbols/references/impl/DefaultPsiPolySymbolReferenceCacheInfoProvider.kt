// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolService
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.references.PsiPolySymbolReferenceCacheInfoProvider

class DefaultPsiPolySymbolReferenceCacheInfoProvider : PsiPolySymbolReferenceCacheInfoProvider {

  override fun getCacheKey(referenceHost: PsiExternalReferenceHost, targetSymbol: Symbol?): Any? =
    targetSymbol
      ?.let {
        // TODO consider using pointers as cache keys
        PsiSymbolService.getInstance().extractElementFromSymbol(it)
        ?: it as? PolySymbol
      }

}