// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.query.PolySymbolsCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.utils.merge
import com.intellij.polySymbols.webTypes.impl.WebTypesJsonContributionAdapter
import com.intellij.polySymbols.webTypes.impl.wrap
import com.intellij.polySymbols.webTypes.json.*
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import javax.swing.Icon

open class WebTypesSymbolBase : WebTypesSymbol {

  private lateinit var base: WebTypesJsonContributionAdapter

  protected lateinit var queryExecutor: PolySymbolsQueryExecutor

  private var _superContributions: List<PolySymbol>? = null

  private val superContributions: List<PolySymbol>
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

  override fun equals(other: Any?): Boolean =
    other === this
    || other is WebTypesSymbolBase
    && other.javaClass == javaClass
    && other.base == base
    && other.queryExecutor === queryExecutor

  override fun hashCode(): Int =
    31 * base.hashCode() + queryExecutor.hashCode()

  override fun createPointer(): Pointer<WebTypesSymbolBase> {
    val queryExecutorPtr = this.queryExecutor.createPointer()
    val basePtr = this.base.createPointer()
    return Pointer<WebTypesSymbolBase> {
      val queryExecutor = queryExecutorPtr.dereference() ?: return@Pointer null
      val base = basePtr.dereference() ?: return@Pointer null
      base.withQueryExecutorContext(queryExecutor) as WebTypesSymbolBase
    }
  }

  internal fun init(webTypesJsonContributionAdapter: WebTypesJsonContributionAdapter, queryExecutor: PolySymbolsQueryExecutor) {
    this.base = webTypesJsonContributionAdapter
    this.queryExecutor = queryExecutor
  }

  final override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsNameMatchQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    base.rootScope
      .getMatchingSymbols(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, scope)
      .toList()

  final override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolsListSymbolsQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolsScope> =
    base.rootScope
      .getSymbols(base.contributionForQuery, this.origin as WebTypesJsonOrigin, qualifiedKind, params)
      .toList()

  final override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsCodeCompletionQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    base.rootScope
      .getCodeCompletions(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, scope)
      .toList()

  final override val qualifiedKind: PolySymbolQualifiedKind
    get() = base.qualifiedKind

  final override val origin: PolySymbolOrigin
    get() = base.jsonOrigin

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

  final override val attributeValue: PolySymbolHtmlAttributeValue?
    get() = (base.contribution.attributeValue?.let { sequenceOf(HtmlAttributeValueImpl(it)) } ?: emptySequence())
      .plus(superContributions.asSequence().map { it.attributeValue })
      .merge()

  final override val type: Any?
    get() = (base.contribution.type)
              ?.let { base.jsonOrigin.typeSupport?.resolve(it.mapToTypeReferences()) }
            ?: superContributions.asSequence().mapNotNull { it.type }.firstOrNull()

  final override val apiStatus: PolySymbolApiStatus
    get() = base.contribution.toApiStatus(origin as WebTypesJsonOrigin)

  final override val virtual: Boolean
    get() = base.contribution.virtual == true

  final override val extension: Boolean
    get() = base.contribution.extension == true

  final override val priority: PolySymbol.Priority?
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

  final override val defaultValue: String?
    get() = (base.contribution as? GenericContribution)?.default
            ?: (base.contribution as? HtmlAttribute)?.default
            ?: superContributions.firstOrNull()?.defaultValue

  final override val pattern: PolySymbolsPattern?
    get() = base.jsonPattern?.wrap(base.contribution.name, origin as WebTypesJsonOrigin)


  final override val queryScope: List<PolySymbolsScope>
    get() = superContributions.asSequence()
      .flatMap { it.queryScope }
      .plus(this)
      .toList()

  final override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    base.isExclusiveFor(qualifiedKind)
    || superContributions.any { it.isExclusiveFor(qualifiedKind) }

  override fun matchContext(context: PolyContext): Boolean =
    super.matchContext(context) && base.contribution.requiredContext.evaluate(context)

  private inner class HtmlAttributeValueImpl(private val value: HtmlAttributeValue) : PolySymbolHtmlAttributeValue {
    override val kind: PolySymbolHtmlAttributeValue.Kind?
      get() = value.kind?.wrap()

    override val type: PolySymbolHtmlAttributeValue.Type?
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