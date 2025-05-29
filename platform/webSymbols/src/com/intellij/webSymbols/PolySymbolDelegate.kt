// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.find.usages.api.SearchTarget
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.documentation.PolySymbolDocumentation
import com.intellij.webSymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.refactoring.WebSymbolRenameTarget
import com.intellij.webSymbols.refactoring.impl.WebSymbolDelegatedRenameTargetImpl
import com.intellij.webSymbols.search.WebSymbolSearchTarget
import com.intellij.webSymbols.search.impl.WebSymbolDelegatedSearchTargetImpl
import javax.swing.Icon

abstract class PolySymbolDelegate<T : PolySymbol>(val delegate: T) : PolySymbol {

  override val psiContext: PsiElement?
    get() = delegate.psiContext
  override val origin: PolySymbolOrigin
    get() = delegate.origin
  override val namespace: SymbolNamespace
    get() = delegate.namespace
  override val kind: SymbolKind
    get() = delegate.kind

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

  override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<PolySymbol> =
    delegate.getMatchingSymbols(qualifiedName, params, scope)


  override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind,
                          params: WebSymbolsListSymbolsQueryParams,
                          scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> =
    delegate.getSymbols(qualifiedKind, params, scope)

  override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> =
    delegate.getCodeCompletions(qualifiedName, params, scope)

  override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    delegate.isExclusiveFor(qualifiedKind)

  override val searchTarget: WebSymbolSearchTarget?
    get() = when (delegate) {
      is SearchTarget -> WebSymbolDelegatedSearchTargetImpl(delegate)
      else -> delegate.searchTarget
    }

  override val renameTarget: WebSymbolRenameTarget?
    get() = when (delegate) {
      is RenameTarget -> WebSymbolDelegatedRenameTargetImpl(delegate)
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