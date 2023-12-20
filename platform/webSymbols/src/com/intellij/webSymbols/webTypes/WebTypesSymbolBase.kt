// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.utils.merge
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionAdapter
import com.intellij.webSymbols.webTypes.impl.wrap
import com.intellij.webSymbols.webTypes.json.*
import com.intellij.webSymbols.webTypes.json.resolve
import javax.swing.Icon

open class WebTypesSymbolBase : WebTypesSymbol {

  private lateinit var base: WebTypesJsonContributionAdapter

  protected lateinit var queryExecutor: WebSymbolsQueryExecutor

  private var _superContributions: List<WebSymbol>? = null

  private val superContributions: List<WebSymbol>
    get() = _superContributions
            ?: base.contribution.extends
              .also { _superContributions = emptyList() }
              ?.resolve(listOf(), queryExecutor, true, true)
              ?.toList()
              ?.also { contributions -> _superContributions = contributions }
            ?: emptyList()

  override val properties: Map<String, Any>
    get() = base.contribution.genericProperties

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    (symbol is WebTypesSymbolBase && symbol.base == this.base)
    || super.isEquivalentTo(symbol)

  override fun toString(): String =
    base.toString()

  override fun createPointer(): Pointer<WebTypesSymbolBase> {
    val queryExecutorPtr = this.queryExecutor.createPointer()
    val basePtr = this.base.createPointer()
    return Pointer<WebTypesSymbolBase> {
      val queryExecutor = queryExecutorPtr.dereference() ?: return@Pointer null
      val base = basePtr.dereference() ?: return@Pointer null
      base.withQueryExecutorContext(queryExecutor) as WebTypesSymbolBase
    }
  }

  internal fun init(webTypesJsonContributionAdapter: WebTypesJsonContributionAdapter, queryExecutor: WebSymbolsQueryExecutor) {
    this.base = webTypesJsonContributionAdapter
    this.queryExecutor = queryExecutor
  }

  final override fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    base.rootScope
      .getMatchingSymbols(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, scope)
      .toList()

  final override fun getSymbols(qualifiedKind: WebSymbolQualifiedKind,
                          params: WebSymbolsListSymbolsQueryParams,
                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    base.rootScope
      .getSymbols(base.contributionForQuery, this.origin, qualifiedKind, params)
      .toList()

  final override fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    base.rootScope
      .getCodeCompletions(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, scope)
      .toList()

  final override val kind: SymbolKind
    get() = base.kind

  final override val origin: WebTypesJsonOrigin
    get() = base.jsonOrigin

  final override val namespace: SymbolNamespace
    get() = base.namespace

  final override val name: String
    get() = if (base is WebTypesJsonContributionAdapter.Pattern) base.contributionName else base.name

  final override val description: String?
    get() = base.contribution.description
              ?.let { base.jsonOrigin.renderDescription(base.contribution.description) }
            ?: superContributions.asSequence().mapNotNull { it.description }.firstOrNull()

  final override val descriptionSections: Map<String, String>
    get() = (base.contribution.descriptionSections?.additionalProperties?.asSequence() ?: emptySequence())
      .plus(superContributions.asSequence().flatMap { it.descriptionSections.asSequence() })
      .distinctBy { it.key }
      .associateBy({ it.key }, { base.jsonOrigin.renderDescription(it.value) })

  final override val docUrl: String?
    get() = base.contribution.docUrl
            ?: superContributions.asSequence().mapNotNull { it.docUrl }.firstOrNull()

  final override val icon: Icon?
    get() = base.icon ?: superContributions.asSequence().mapNotNull { it.icon }.firstOrNull()

  final override val location: WebTypesSymbol.Location?
    // Should not reach to super contributions, because it can lead to stack overflow
    // when special containers are trying to merge symbols
    get() = base.contribution.source
      ?.let {
        base.jsonOrigin.resolveSourceLocation(it)
      }

  final override val source: PsiElement?
    // Should not reach to super contributions, because it can lead to stack overflow
    // when special containers are trying to merge symbols
    get() = base.contribution.source
      ?.let {
        base.jsonOrigin.resolveSourceSymbol(it, base.cacheHolder)
      }

  final override val attributeValue: WebSymbolHtmlAttributeValue?
    get() = (base.contribution.attributeValue?.let { sequenceOf(HtmlAttributeValueImpl(it)) } ?: emptySequence())
      .plus(superContributions.asSequence().map { it.attributeValue })
      .merge()

  final override val type: Any?
    get() = (base.contribution.type)
              ?.let { base.jsonOrigin.typeSupport?.resolve(it.mapToTypeReferences()) }
            ?: superContributions.asSequence().mapNotNull { it.type }.firstOrNull()

  final override val apiStatus: WebSymbolApiStatus
    get() = base.contribution.toApiStatus(origin)

  final override val virtual: Boolean
    get() = base.contribution.virtual == true

  final override val extension: Boolean
    get() = base.contribution.extension == true

  final override val priority: WebSymbol.Priority?
    get() = base.contribution.priority?.wrap()
            ?: superContributions.firstOrNull()?.priority

  final override val proximity: Int?
    get() = base.contribution.proximity
            ?: superContributions.firstOrNull()?.proximity

  final override val abstract: Boolean
    get() = base.contribution.abstract == true

  final override val required: Boolean?
    get() = (base.contribution as? GenericContribution)?.required
            ?: (base.contribution as? HtmlAttribute)?.required
            ?: superContributions.firstOrNull()?.required

  final  override val defaultValue: String?
    get() = (base.contribution as? GenericContribution)?.default
            ?: (base.contribution as? HtmlAttribute)?.default
            ?: superContributions.firstOrNull()?.defaultValue

  final override val pattern: WebSymbolsPattern?
    get() = base.jsonPattern?.wrap(base.contribution.name, origin)


  final override val queryScope: List<WebSymbolsScope>
    get() = superContributions.asSequence()
      .flatMap { it.queryScope }
      .plus(this)
      .toList()

  final override fun isExclusiveFor(qualifiedKind: WebSymbolQualifiedKind): Boolean =
    base.isExclusiveFor(qualifiedKind)
        || superContributions.any { it.isExclusiveFor(qualifiedKind) }

  private inner class HtmlAttributeValueImpl(private val value: HtmlAttributeValue) : WebSymbolHtmlAttributeValue {
    override val kind: WebSymbolHtmlAttributeValue.Kind?
      get() = value.kind?.wrap()

    override val type: WebSymbolHtmlAttributeValue.Type?
      get() = value.type?.wrap()

    override val required: Boolean?
      get() = value.required

    override val default: String?
      get() = value.default

    override val langType: Any?
      get() = value.type?.toLangType()
        ?.let { base.jsonOrigin.typeSupport?.resolve(it.mapToTypeReferences()) }

  }
}