// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

interface PsiPolySymbolReferenceCacheInfoProvider {

  fun getCacheKey(referenceHost: PsiExternalReferenceHost, targetSymbol: Symbol?): Any?

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PsiPolySymbolReferenceCacheInfoProvider> =
      ExtensionPointName.create<PsiPolySymbolReferenceCacheInfoProvider>("com.intellij.polySymbols.psiReferenceCacheInfoProvider")

    @ApiStatus.Internal
    fun getCacheKeys(referenceHost: PsiExternalReferenceHost, targetSymbol: Symbol?): List<Any?> =
      EP_NAME.extensionList.map { it.getCacheKey(referenceHost, targetSymbol) }

  }
}