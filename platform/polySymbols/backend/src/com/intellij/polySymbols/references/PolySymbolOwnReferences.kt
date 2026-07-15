// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.references.impl.PolySymbolOwnReferencesBuilderImpl
import com.intellij.psi.PsiElement

/**
 * Provides support for building own references meant to be returned directly from [PsiElement.getOwnReferences],
 * instead of registering a `polySymbols.psiReferenceProvider` EP (which produces *external* references — see
 * [PsiPolySymbolReferenceProvider]'s doc for the distinction). Own references, once non-empty, take priority
 * over external ones for resolve/search/rename, so a host should use one mechanism or the other, not both.
 */
class PolySymbolOwnReferences internal constructor(
  val referencedSymbols: Map<Int, PolySymbol>,
  val references: List<PolySymbolReference>,
) {

  companion object {
    @JvmStatic
    fun build(element: PsiElement, configure: Builder.() -> Unit): PolySymbolOwnReferences {
      val builder = PolySymbolOwnReferencesBuilderImpl(element)
      builder.configure()
      return builder.build()
    }
  }

  interface Builder {
    fun fromNameMatchQuery(kind: PolySymbolKind, name: String)
    fun fromNameMatchQuery(kind: PolySymbolKind, name: String, filter: (PolySymbol) -> Boolean)
    fun reference(symbol: PolySymbol)
    fun reference(symbol: PolySymbol, offset: Int = 0, showProblems: Boolean = true)
    fun references(offsetsToSymbols: Map<Int, PolySymbol>, showProblems: Boolean = true)
  }
}

fun polySymbolOwnReferences(element: PsiElement, configure: PolySymbolOwnReferences.Builder.() -> Unit): PolySymbolOwnReferences =
  PolySymbolOwnReferences.build(element, configure)

