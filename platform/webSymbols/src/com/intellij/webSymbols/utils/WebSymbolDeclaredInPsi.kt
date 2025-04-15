// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.declarations.WebSymbolDeclaration

interface WebSymbolDeclaredInPsi : WebSymbol, SearchTarget, RenameTarget {

  val sourceElement: PsiElement?

  val textRangeInSourceElement: TextRange?

  override val psiContext: PsiElement?
    get() = sourceElement

  val declaration: WebSymbolDeclaration?
    get() =
      buildDeclaration(this)

  override fun createPointer(): Pointer<out WebSymbolDeclaredInPsi>

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    return listOf(PsiNavigatableWebSymbolNavigationTarget(this))
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

  class PsiNavigatableWebSymbolNavigationTarget internal constructor(private val symbol: WebSymbolDeclaredInPsi) : NavigationTarget {

    override fun createPointer(): Pointer<out NavigationTarget> {
      val symbolPtr = symbol.createPointer()
      return Pointer {
        symbolPtr.dereference()?.let { PsiNavigatableWebSymbolNavigationTarget(it) }
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

private fun buildDeclaration(symbol: WebSymbolDeclaredInPsi): WebSymbolDeclaration? {
  return WebSymbolDeclaredInPsiDeclaration(symbol, symbol.sourceElement ?: return null,
                                           symbol.textRangeInSourceElement ?: return null)
}

private class WebSymbolDeclaredInPsiDeclaration(private val symbol: WebSymbol,
                                                private val element: PsiElement,
                                                private val range: TextRange) : WebSymbolDeclaration {
  override fun getDeclaringElement(): PsiElement = element
  override fun getRangeInDeclaringElement(): TextRange = range
  override fun getSymbol(): WebSymbol = symbol
}