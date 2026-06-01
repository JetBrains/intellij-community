// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.utils.PolySymbolDeclaredInPsi
import com.intellij.psi.PsiElement

/**
 * This interface servers as a bridge between Symbol-based functionality and the old
 * PSI-based functionality. Implementing this interface on a [PolySymbol] links it directly
 * to the [PsiElement] returned by the [linkedElement] property.
 *
 * Returned [PsiElement] class (or one of it's super classes) should be registered as a `host`
 * through `com.intellij.polySymbols.psiLinkedSymbol`.
 *
 * If your symbol is part of a [PsiElement] (e.g. part of a string literal), or spans multiple PSI elements,
 * or does not relate 1-1 with a PSI element, instead of implementing this interface you should contribute
 * dedicated declaration provider ([com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider]).
 *
 * When a [PsiElement] usages are being searched for, or the element is being renamed,
 * any references, which resolve to a [PolySymbolDeclaredInPsi], of which [linkedElement]
 * property is equivalent to the [PsiElement], are recognized as references to the symbol
 * and are being returned as usages, or rename usages.
 *
 * It works the other way too, so if a usage search or rename is performed on a reference
 * to a [PolySymbolDeclaredInPsi], the usage search or rename is also run for [linkedElement].
 *
 * The PolySymbol, which implements this interface, should not override [renameTarget] or
 * [searchTarget] properties, as the framework already handles this functionality.
 *
 * To properly support search and rename refactoring for symbols, which names can be modified
 * by [com.intellij.polySymbols.query.PolySymbolNameConversionRules], a `PsiLinkedPolySymbolProvider`
 * should be implemented to allow the framework to search for alternative names.
 *
 * See also: [Declarations, References, Search, Refactoring](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#declarations-references-search-refactoring)
 *
 * @see [PolySymbolDeclaredInPsi]
 *
 */
interface PsiLinkedPolySymbol : PolySymbol {

  override val psiContext: PsiElement?
    get() = linkedElement

  /**
   * The [PsiElement], which is the symbol declaration.
   */
  val linkedElement: PsiElement?

  override fun createPointer(): Pointer<out PsiLinkedPolySymbol>

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    linkedElement?.let { listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(it)) } ?: emptyList()

  override fun isEquivalentTo(symbol: Symbol): Boolean {
    if (this == symbol) return true
    val source = this.linkedElement ?: return false
    val target = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
    return when {
      target != null -> target.manager.areElementsEquivalent(source, target)
      symbol is PsiLinkedPolySymbol -> source == symbol.linkedElement
      else -> false
    }
  }

}