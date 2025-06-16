// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.json.CustomElementClassOrMixinDeclaration
import com.intellij.polySymbols.customElements.json.resolve
import com.intellij.polySymbols.customElements.json.toApiStatus
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.psi.PsiElement

class CustomElementsClassOrMixinDeclarationAdapter private constructor(
  override val name: String,
  private val declaration: CustomElementClassOrMixinDeclaration,
  private val origin: CustomElementsJsonOrigin,
  private val rootScope: CustomElementsManifestScopeBase,
) : StaticPolySymbolScopeBase.StaticSymbolContributionAdapter {

  private val cacheHolder = UserDataHolderBase()

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = CustomElementsSymbol.CEM_DECLARATIONS

  override val pattern: PolySymbolPattern?
    get() = null

  override val framework: FrameworkId?
    get() = null

  override fun withQueryExecutorContext(queryExecutor: PolySymbolQueryExecutor): PolySymbol =
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
    private val queryExecutor: PolySymbolQueryExecutor,
  ) : CustomElementsSymbol, PsiSourcedPolySymbol {

    private var _superContributions: List<PolySymbol>? = null

    private val superContributions: List<PolySymbol>
      get() = _superContributions
              ?: (base.declaration.mixins + listOfNotNull(base.declaration.superclass))
                .also { _superContributions = emptyList() }
                .flatMap { it.resolve(origin, queryExecutor) }
                .toList()
                .also { contributions -> _superContributions = contributions }

    override fun getModificationCount(): Long = 0

    override val origin: CustomElementsJsonOrigin
      get() = base.origin

    override val qualifiedKind: PolySymbolQualifiedKind
      get() = base.qualifiedKind

    override val name: String
      get() = base.name

    override val description: String?
      get() = (base.declaration.description?.takeIf { it.isNotBlank() } ?: base.declaration.summary)
                ?.let { origin.renderDescription(it) }
              ?: superContributions.asSequence()
                .mapNotNull { (it as? PolySymbolWithDocumentation)?.description }
                .firstOrNull()

    override val apiStatus: PolySymbolApiStatus
      get() = base.declaration.deprecated.toApiStatus(origin) ?: PolySymbolApiStatus.Stable

    override val queryScope: List<PolySymbolScope>
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

    override fun getMatchingSymbols(
      qualifiedName: PolySymbolQualifiedName,
      params: PolySymbolNameMatchQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> =
      base.rootScope
        .getMatchingSymbols(base.declaration, this.origin, qualifiedName, params, stack)
        .toList()

    override fun getSymbols(
      qualifiedKind: PolySymbolQualifiedKind,
      params: PolySymbolListSymbolsQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> =
      base.rootScope
        .getSymbols(base.declaration, this.origin, qualifiedKind, params)
        .toList()

    override fun getCodeCompletions(
      qualifiedName: PolySymbolQualifiedName,
      params: PolySymbolCodeCompletionQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbolCodeCompletionItem> =
      base.rootScope
        .getCodeCompletions(base.declaration, this.origin, qualifiedName, params, stack)
        .toList()
  }

  companion object {
    fun create(
      declaration: CustomElementClassOrMixinDeclaration,
      origin: CustomElementsJsonOrigin,
      rootScope: CustomElementsManifestScopeBase,
    ): CustomElementsClassOrMixinDeclarationAdapter? {
      val name = declaration.name
      if (name == null) {
        return null
      }
      return CustomElementsClassOrMixinDeclarationAdapter(name, declaration, origin, rootScope)
    }
  }

}