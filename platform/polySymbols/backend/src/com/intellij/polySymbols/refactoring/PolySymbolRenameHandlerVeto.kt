// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring

import com.intellij.openapi.util.Condition
import com.intellij.polySymbols.PolySymbolEnabledLanguage
import com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Vetoes the classic [com.intellij.refactoring.rename.PsiElementRenameHandler] for a
 * [PsiNameIdentifierOwner] whose name identifier range is already covered by a
 * [PolySymbolDeclarationProvider] declaration.
 *
 * A [com.intellij.polySymbols.utils.PolySymbolDeclaredInPsi] symbol is its own
 * [com.intellij.refactoring.rename.api.RenameTarget], but the interface does not link the symbol
 * back to the declaring [PsiElement] (see its own doc comment). Without this veto, "Rename" offers
 * both the classic PSI rename and the PolySymbol-based rename as separate, redundant choices for the
 * same declaration.
 */
internal class PolySymbolRenameHandlerVeto : Condition<PsiElement> {

  override fun value(element: PsiElement): Boolean {
    if (!PolySymbolEnabledLanguage.matchesLanguage(element.language)) return false
    val nameIdentifier = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: return false
    val identifierRange = nameIdentifier.textRange
    return PolySymbolDeclarationProvider.getAllDeclarations(element, -1).any { declaration ->
      declaration.absoluteRange == identifierRange
    }
  }

}
