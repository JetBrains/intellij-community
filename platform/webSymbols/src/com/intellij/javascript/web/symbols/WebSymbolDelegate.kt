// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javascript.web.symbols

import com.intellij.javascript.web.symbols.patterns.WebSymbolsPattern
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.util.containers.Stack
import javax.swing.Icon

abstract class WebSymbolDelegate<T : WebSymbol>(val delegate: T) : WebSymbol {

  override val psiContext: PsiElement?
    get() = delegate.psiContext
  override val origin: WebSymbolsContainer.Origin
    get() = delegate.origin
  override val namespace: WebSymbolsContainer.Namespace
    get() = delegate.namespace
  override val kind: SymbolKind
    get() = delegate.kind
  override val matchedName: String
    get() = delegate.matchedName

  override fun getModificationCount(): Long =
    delegate.modificationCount

  override val completeMatch: Boolean
    get() = delegate.completeMatch
  override val nameSegments: List<WebSymbol.NameSegment>
    get() = delegate.nameSegments
  override val contextContainers: Sequence<WebSymbolsContainer>
    get() = delegate.contextContainers
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
  override val jsType: Any?
    get() = delegate.jsType
  override val attributeValue: WebSymbol.AttributeValue?
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

  override fun getSymbols(namespace: WebSymbolsContainer.Namespace?,
                          kind: SymbolKind,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    delegate.getSymbols(namespace, kind, name, params, context)

  override fun getCodeCompletions(namespace: WebSymbolsContainer.Namespace?,
                                  kind: SymbolKind,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    delegate.getCodeCompletions(namespace, kind, name, params, context)

  override fun isExclusiveFor(namespace: WebSymbolsContainer.Namespace?, kind: SymbolKind): Boolean =
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