// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.json.CustomElementClassOrMixinDeclaration
import com.intellij.webSymbols.customElements.json.resolve
import com.intellij.webSymbols.customElements.json.toApiStatus
import com.intellij.webSymbols.impl.StaticWebSymbolsScopeBase
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class CustomElementsClassOrMixinDeclarationAdapter private constructor(
  override val name: String,
  private val declaration: CustomElementClassOrMixinDeclaration,
  private val origin: CustomElementsJsonOrigin,
  private val rootScope: CustomElementsManifestScopeBase
) : StaticWebSymbolsScopeBase.StaticSymbolContributionAdapter {

  private val cacheHolder = UserDataHolderBase()

  override val namespace: SymbolNamespace
    get() = CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST

  override val kind: String
    get() = CustomElementsSymbol.KIND_CEM_DECLARATIONS

  override val pattern: WebSymbolsPattern?
    get() = null

  override val framework: FrameworkId?
    get() = null

  override fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): WebSymbol =
    CustomElementClassOrMixinDeclarationSymbol(this, queryExecutor)

  private fun createPointer(): Pointer<CustomElementsClassOrMixinDeclarationAdapter> {
    val name = name
    val declaration = declaration
    val origin = origin
    val rootScopePtr = rootScope.createPointer()
    return Pointer {
      rootScopePtr.dereference()?.let {
        CustomElementsClassOrMixinDeclarationAdapter(name, declaration, origin, it)
      }
    }
  }

  private class CustomElementClassOrMixinDeclarationSymbol(
    private val base: CustomElementsClassOrMixinDeclarationAdapter,
    private val queryExecutor: WebSymbolsQueryExecutor,
  ) : CustomElementsSymbol, PsiSourcedWebSymbol {

    private var _superContributions: List<WebSymbol>? = null

    private val superContributions: List<WebSymbol>
      get() = _superContributions
              ?: (base.declaration.mixins + listOfNotNull(base.declaration.superclass))
                .also { _superContributions = emptyList() }
                .flatMap { it.resolve(origin, queryExecutor) }
                .toList()
                .also { contributions -> _superContributions = contributions }

    override val origin: CustomElementsJsonOrigin
      get() = base.origin

    override val namespace: SymbolNamespace
      get() = base.namespace

    override val kind: SymbolKind
      get() = base.kind

    override val name: String
      get() = base.name
    override val description: String?
      get() = (base.declaration.description?.takeIf { it.isNotBlank() } ?: base.declaration.summary)
                ?.let { origin.renderDescription(it) }
              ?: superContributions.asSequence().mapNotNull { it.description }.firstOrNull()

    override val apiStatus: WebSymbolApiStatus
      get() = base.declaration.deprecated.toApiStatus(origin) ?: WebSymbolApiStatus.Stable

    override val queryScope: List<WebSymbolsScope>
      get() = superContributions.asSequence()
        .flatMap { it.queryScope }
        .plus(this)
        .toList()

    override val source: PsiElement?
      get() = base.declaration.source?.let { origin.resolveSourceSymbol(it, base.cacheHolder) }

    override fun createPointer(): Pointer<CustomElementClassOrMixinDeclarationSymbol> {
      val queryExecutorPtr = queryExecutor.createPointer()
      val basePtr = base.createPointer()
      return Pointer<CustomElementClassOrMixinDeclarationSymbol> {
        val queryExecutor = queryExecutorPtr.dereference() ?: return@Pointer null
        val base = basePtr.dereference() ?: return@Pointer null
        base.withQueryExecutorContext(queryExecutor) as CustomElementClassOrMixinDeclarationSymbol
      }
    }

    override fun getMatchingSymbols(namespace: SymbolNamespace,
                                    kind: String,
                                    name: String,
                                    params: WebSymbolsNameMatchQueryParams,
                                    scope: Stack<WebSymbolsScope>): List<WebSymbol> =
      base.rootScope
        .getMatchingSymbols(base.declaration, this.origin, namespace,
                            kind, name, params, scope)
        .toList()

    override fun getSymbols(namespace: SymbolNamespace,
                            kind: SymbolKind,
                            params: WebSymbolsListSymbolsQueryParams,
                            scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
      base.rootScope
        .getSymbols(base.declaration, this.origin, namespace,
                    kind, params)
        .toList()

    override fun getCodeCompletions(namespace: SymbolNamespace,
                                    kind: String,
                                    name: String,
                                    params: WebSymbolsCodeCompletionQueryParams,
                                    scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
      base.rootScope
        .getCodeCompletions(base.declaration, this.origin, namespace,
                            kind, name, params, scope)
        .toList()


  }

  companion object {
    fun create(declaration: CustomElementClassOrMixinDeclaration,
               origin: CustomElementsJsonOrigin,
               rootScope: CustomElementsManifestScopeBase): CustomElementsClassOrMixinDeclarationAdapter? {
      val name = declaration.name
      if (name == null) {
        return null
      }
      return CustomElementsClassOrMixinDeclarationAdapter(name, declaration, origin, rootScope)
    }
  }

}