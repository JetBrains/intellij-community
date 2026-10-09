// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.declarations.PolySymbolDeclaration
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget

/**
 * This interface servers as a boilerplate to define symbols that are declared somewhere in PSI.
 * It provides out-of-the-box support for navigation to the declaration,
 * for find usages, for rename and for building [PolySymbolDeclaration] object.
 *
 * You need to override [sourceElement] and [textRangeInSourceElement] to provide
 * the PSI element within which the symbol is declared and the range of the declaration within that element.
 * You should also implement [`PolySymbolDeclarationProvider`](com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider),
 * which would instantiate (or find) the symbol object and return the [declaration].
 * Providing a declaration through the provider is important because it allows the platform
 * to recognize the symbol declaration for the specified code range and provide functionality
 * (documentation, find usages, rename, etc.).
 *
 * In contrast to [PsiLinkedPolySymbol], implementing this interface
 * does not link the symbol to the PSI element, so any usage or rename
 * searches for the [PsiElement] returned by the [sourceElement] will not
 * result in the symbol being recognized as a usage or rename target.
 *
 * @see [PsiLinkedPolySymbol]
 *
 */
interface PolySymbolDeclaredInPsi : PolySymbol, SearchTarget, RenameTarget {

  val sourceElement: PsiElement?

  val textRangeInSourceElement: TextRange?

  override val psiContext: PsiElement?
    get() = sourceElement

  val declaration: PolySymbolDeclaration?
    get() =
      buildDeclaration(this)

  override fun createPointer(): Pointer<out PolySymbolDeclaredInPsi>

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    return listOf(PsiNavigatablePolySymbolNavigationTarget(this))
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(name)

  override val targetName: String
    get() = name

  override val maximalSearchScope: SearchScope?
    get() = super<SearchTarget>.maximalSearchScope

  override fun presentation(): TargetPresentation {
    return presentation
  }

  class PsiNavigatablePolySymbolNavigationTarget internal constructor(private val symbol: PolySymbolDeclaredInPsi) : NavigationTarget {

    override fun createPointer(): Pointer<out NavigationTarget> {
      val symbolPtr = symbol.createPointer()
      return Pointer {
        symbolPtr.dereference()?.let { PsiNavigatablePolySymbolNavigationTarget(it) }
      }
    }

    override fun computePresentation(): TargetPresentation {
      return symbol.presentation
    }

    fun getNavigationItem(): NavigationItem? {
      return createPsiRangeNavigationItem(symbol.sourceElement ?: return null,
                                          symbol.textRangeInSourceElement?.startOffset ?: return null) as? NavigationItem
    }

    override fun navigationRequest(): NavigationRequest? {
      return getNavigationItem()?.navigationRequest()
    }
  }
}

private fun buildDeclaration(symbol: PolySymbolDeclaredInPsi): PolySymbolDeclaration? {
  return PolySymbolDeclaredInPsiDeclaration(symbol, symbol.sourceElement ?: return null,
                                            symbol.textRangeInSourceElement ?: return null)
}

private class PolySymbolDeclaredInPsiDeclaration(
  private val symbol: PolySymbol,
  private val element: PsiElement,
  private val range: TextRange,
) : PolySymbolDeclaration {
  override fun getDeclaringElement(): PsiElement = element
  override fun getRangeInDeclaringElement(): TextRange = range
  override fun getSymbol(): PolySymbol = symbol
}