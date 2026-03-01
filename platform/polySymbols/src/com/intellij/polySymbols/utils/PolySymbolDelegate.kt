// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.refactoring.impl.PolySymbolDelegatedRenameTargetImpl
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.search.impl.PolySymbolDelegatedSearchTargetImpl
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import javax.swing.Icon

interface PolySymbolDelegate<T : PolySymbol> : PolySymbol, PolySymbolScope {

  val delegate: T

  override val psiContext: PsiElement?
    get() = delegate.psiContext
  override val kind: PolySymbolKind
    get() = delegate.kind
  override val queryScope: List<PolySymbolScope>
    get() = delegate.queryScope
  override val name: String
    get() = delegate.name
  override val icon: Icon?
    get() = delegate.icon
  override val apiStatus: PolySymbolApiStatus
    get() = delegate.apiStatus
  override val modifiers: Set<PolySymbolModifier>
    get() = delegate.modifiers
  override val extension: Boolean
    get() = delegate.extension
  override val priority: PolySymbol.Priority?
    get() = delegate.priority

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    super.get(property) ?: delegate[property]

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    delegate.getDocumentationTarget(location)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    (delegate as? NavigatableSymbol)?.getNavigationTargets(project) ?: emptyList()

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    (delegate as? PolySymbolScope)?.getMatchingSymbols(qualifiedName, params, stack)
    ?: emptyList()

  override fun getSymbols(
    kind: PolySymbolKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    (delegate as? PolySymbolScope)?.getSymbols(kind, params, stack)
    ?: emptyList()

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    (delegate as? PolySymbolScope)?.getCodeCompletions(qualifiedName, params, stack)
    ?: emptyList()

  override fun isExclusiveFor(kind: PolySymbolKind): Boolean =
    (delegate as? PolySymbolScope)?.isExclusiveFor(kind)
    ?: false

  override val searchTarget: PolySymbolSearchTarget?
    get() = when (delegate) {
      is SearchTarget -> PolySymbolDelegatedSearchTargetImpl(delegate)
      else -> delegate.searchTarget
    }

  override val renameTarget: PolySymbolRenameTarget?
    get() = when (delegate) {
      is RenameTarget -> PolySymbolDelegatedRenameTargetImpl(delegate)
      else -> delegate.renameTarget
    }

  override fun createPointer(): Pointer<out PolySymbolDelegate<T>>

  override fun getModificationCount(): Long =
    (delegate as? PolySymbolScope)?.modificationCount ?: 0

  companion object {

    @JvmStatic
    fun PolySymbol.unwrapAllDelegates(): PolySymbol {
      var result = this
      while (result is PolySymbolDelegate<*>) {
        result = result.delegate
      }
      return result
    }

  }
}