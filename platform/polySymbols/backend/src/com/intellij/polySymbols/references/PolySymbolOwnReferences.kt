// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.references.impl.PolySymbolOwnReferencesBuilderImpl
import com.intellij.psi.PsiElement

/**
 * Provides support for building own references meant to be returned directly from [PsiElement.getOwnReferences],
 * instead of registering a `polySymbols.psiReferenceProvider` EP (which produces *external* references — see
 * [PsiPolySymbolReferenceProvider]'s doc for the distinction). Own references, once non-empty, take priority
 * over external ones for resolve/search/rename, so a host should use one mechanism or the other, not both.
 *
 * Own references support only simple kind-reference PolySymbol patterns but are lazily evaluated.
 * If you want to use complex patterns, consider using [PsiPolySymbolReferenceProvider] instead
 * and implement [com.intellij.model.psi.PsiExternalReferenceHost], which provide more flexibility through
 * eager reference resolution.
 */
fun polySymbolOwnReferences(element: PsiElement, configure: PolySymbolOwnReferencesBuilder.() -> Unit): List<PolySymbolReference> =
  PolySymbolOwnReferencesBuilderImpl(element).also { it.configure() }.build()

interface PolySymbolOwnReferencesBuilder {
  fun resolveFromNameMatchQuery(kind: PolySymbolKind, name: String)
  fun resolveFromNameMatchQuery(kind: PolySymbolKind, name: String, filter: (PolySymbol) -> Boolean)
  fun resolveFromNameMatchQuery(kind: PolySymbolKind, name: String, textRangeInElement: TextRange)
  fun resolveFromNameMatchQuery(kind: PolySymbolKind, name: String, textRangeInElement: TextRange, filter: (PolySymbol) -> Boolean)
  fun reference(textRangeInElement: TextRange, kind: PolySymbolKind, resolver: () -> List<PolySymbol>)
}

