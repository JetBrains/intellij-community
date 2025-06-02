// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.find.usages.api.SearchTarget
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.query.PolySymbolsCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.refactoring.impl.PolySymbolDelegatedRenameTargetImpl
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.search.impl.PolySymbolDelegatedSearchTargetImpl
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.util.containers.Stack
import javax.swing.Icon

abstract class PolySymbolDelegate<T : PolySymbol>(val delegate: T) : PolySymbol {

  override val psiContext: PsiElement?
    get() = delegate.psiContext
  override val origin: PolySymbolOrigin
    get() = delegate.origin
  override val qualifiedKind: PolySymbolQualifiedKind
    get() = delegate.qualifiedKind

  override fun getModificationCount(): Long =
    delegate.modificationCount

  override val queryScope: List<PolySymbolsScope>
    get() = delegate.queryScope
  override val name: String
    get() = delegate.name
  override val description: String?
    get() = delegate.description
  override val descriptionSections: Map<String, String>
    get() = delegate.descriptionSections
  override val docUrl: String?
    get() = delegate.docUrl
  override val icon: Icon?
    get() = delegate.icon
  override val apiStatus: PolySymbolApiStatus
    get() = delegate.apiStatus
  override val virtual: Boolean
    get() = delegate.virtual
  override val abstract: Boolean
    get() = delegate.abstract
  override val extension: Boolean
    get() = delegate.extension
  override val required: Boolean?
    get() = delegate.required
  override val defaultValue: String?
    get() = delegate.defaultValue
  override val priority: PolySymbol.Priority?
    get() = delegate.priority
  override val proximity: Int?
    get() = delegate.proximity
  override val type: Any?
    get() = delegate.type
  override val attributeValue: PolySymbolHtmlAttributeValue?
    get() = delegate.attributeValue
  override val pattern: PolySymbolsPattern?
    get() = delegate.pattern
  override val properties: Map<String, Any>
    get() = delegate.properties

  override fun createDocumentation(location: PsiElement?): PolySymbolDocumentation? =
    delegate.createDocumentation(location)

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    delegate.getDocumentationTarget(location)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    (delegate as? NavigatableSymbol)?.getNavigationTargets(project) ?: emptyList()

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsNameMatchQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    delegate.getMatchingSymbols(qualifiedName, params, scope)


  override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolsListSymbolsQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolsScope> =
    delegate.getSymbols(qualifiedKind, params, scope)

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsCodeCompletionQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    delegate.getCodeCompletions(qualifiedName, params, scope)

  override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    delegate.isExclusiveFor(qualifiedKind)

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