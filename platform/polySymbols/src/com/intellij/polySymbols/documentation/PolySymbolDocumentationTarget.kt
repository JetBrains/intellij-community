// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationBuilderImpl
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationTargetImpl
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolDocumentationTarget : DocumentationTarget {

  val symbol: PolySymbol

  val location: PsiElement?

  val documentation: PolySymbolDocumentation

  companion object {

    /**
     * Provided builder lambda should use symbol and location parameters,
     * since the documentation can be created lazily in another read action
     * and both symbol and location can be dereferenced from pointers.
     */
    @JvmStatic
    fun <T: PolySymbol> create(
      symbol: T,
      location: PsiElement?,
      builder: (PolySymbolDocumentationBuilder.(symbol: T, location: PsiElement?) -> Unit),
    ): PolySymbolDocumentationTarget =
      PolySymbolDocumentationTargetImpl(symbol, location) { symbol, location ->
        PolySymbolDocumentationBuilderImpl(symbol, location)
          .also { it.builder(symbol, location) }
          .build()
      }
        .also { PolySymbolDocumentationTargetImpl.check(builder) }

    /**
     * The provider should use symbol and location parameters,
     * since the documentation can be created lazily in another read action
     * and both symbol and location can be dereferenced from pointers.
     */
    @JvmStatic
    fun <T: PolySymbol> create(
      symbol: T,
      location: PsiElement?,
      provider: PolySymbolDocumentationProvider<T>,
    ): PolySymbolDocumentationTarget =
      PolySymbolDocumentationTargetImpl(symbol, location, provider)
        .also { PolySymbolDocumentationTargetImpl.check(provider) }

  }

}

