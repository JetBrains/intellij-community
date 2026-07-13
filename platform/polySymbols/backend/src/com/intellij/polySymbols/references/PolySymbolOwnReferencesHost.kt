// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.polySymbols.highlighting.impl.PolySymbolHighlightingAnnotator
import org.jetbrains.annotations.ApiStatus

/**
 * This interface allows [PolySymbolHighlightingAnnotator] to support symbol-kind highlighting
 * for own references through [getPolySymbolOwnReferences] result. Implement [buildOwnReferences]
 * method to populate [builder] with this element's own references.
 */
interface PolySymbolOwnReferencesHost : PsiElement {

  /** Populate [builder] with this element's own references. */
  fun buildOwnReferences(builder: PolySymbolOwnReferences.Builder)

  @ApiStatus.NonExtendable
  fun getPolySymbolOwnReferences(): PolySymbolOwnReferences =
    CachedValuesManager.getCachedValue(this) {
      CachedValueProvider.Result.create(
        polySymbolOwnReferences(this, this::buildOwnReferences),
        PsiModificationTracker.MODIFICATION_COUNT
      )
    }

  override fun getOwnReferences(): Collection<PsiSymbolReference> =
    getPolySymbolOwnReferences().references

}