// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import javax.swing.Icon

abstract class WebSymbolDelegate<T : WebSymbol>(val delegate: T) : WebSymbol {

  override val psiContext: PsiElement?
    get() = delegate.psiContext
  override val origin: WebSymbolOrigin
    get() = delegate.origin
  override val namespace: SymbolNamespace
    get() = delegate.namespace
  override val kind: SymbolKind
    get() = delegate.kind
  override fun getModificationCount(): Long =
    delegate.modificationCount
  override val queryScope: Sequence<WebSymbolsScope>
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
  override val deprecated: Boolean
    get() = delegate.deprecated
  override val experimental: Boolean
    get() = delegate.experimental
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
  override val priority: WebSymbol.Priority?
    get() = delegate.priority
  override val proximity: Int?
    get() = delegate.proximity
  override val type: Any?
    get() = delegate.type
  override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = delegate.attributeValue
  override val pattern: WebSymbolsPattern?
    get() = delegate.pattern
  override val properties: Map<String, Any>
    get() = delegate.properties
  override val documentation: WebSymbolDocumentation?
    get() = delegate.documentation

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.documentationTarget

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    (delegate as? NavigatableSymbol)?.getNavigationTargets(project) ?: emptyList()

  override fun getSymbols(namespace: SymbolNamespace?,
                          kind: SymbolKind,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    delegate.getSymbols(namespace, kind, name, params, scope)

  override fun getCodeCompletions(namespace: SymbolNamespace?,
                                  kind: SymbolKind,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    delegate.getCodeCompletions(namespace, kind, name, params, scope)

  override fun isExclusiveFor(namespace: SymbolNamespace?, kind: SymbolKind): Boolean =
    delegate.isExclusiveFor(namespace, kind)

  protected fun renameTargetFromDelegate(): RenameTarget =
    when (delegate) {
      is RenameableSymbol -> delegate.renameTarget
      is RenameTarget -> delegate
      else -> throw IllegalArgumentException(delegate::class.java.toString())
    }

  companion object {

    @JvmStatic
    fun WebSymbol.unwrapAllDelegates(): WebSymbol {
      var result = this
      while (result is WebSymbolDelegate<*>) {
        result = result.delegate
      }
      return result
    }

  }
}