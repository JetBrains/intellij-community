// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationTargetImpl
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolDocumentationTarget : DocumentationTarget {

  val symbol: PolySymbol

  val location: PsiElement?

  val documentation: PolySymbolDocumentation

  fun interface PresentationProvider<T: PolySymbol> {
    fun getPresentation(symbol: T): TargetPresentation
  }

  companion object {

    /**
     * Provided builder lambda should use symbol and location parameters,
     * since the documentation can be created lazily in another read action
     * and both symbol and location can be dereferenced from pointers.
     */
    @JvmStatic
    fun <T : PolySymbol> create(
      symbol: T,
      location: PsiElement?,
      builder: (PolySymbolDocumentationBuilder.(symbol: T, location: PsiElement?) -> Unit),
    ): PolySymbolDocumentationTarget =
      PolySymbolDocumentationTargetImpl(symbol, location, null, builder)
        .also { PolySymbolDocumentationTargetImpl.check(builder) }

    /**
     * Provided builder lambda should use symbol and location parameters,
     * since the documentation can be created lazily in another read action
     * and both symbol and location can be dereferenced from pointers.
     */
    @JvmStatic
    fun <T : PolySymbol> create(
      symbol: T,
      location: PsiElement?,
      presentationProvider: PresentationProvider<T>,
      builder: (PolySymbolDocumentationBuilder.(symbol: T, location: PsiElement?) -> Unit),
    ): PolySymbolDocumentationTarget =
      PolySymbolDocumentationTargetImpl(symbol, location, presentationProvider, builder)
        .also { PolySymbolDocumentationTargetImpl.check(builder) }
  }

}

