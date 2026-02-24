// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol.DocHideIconProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolDocumentationProvider
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.framework.framework
import com.intellij.polySymbols.html.HtmlAttributeValueProperty
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.html.htmlAttributeValue
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.utils.PolySymbolTypeSupport.Companion.PROP_TYPE_SUPPORT
import com.intellij.polySymbols.utils.merge
import com.intellij.polySymbols.webTypes.WebTypesSymbol.Companion.PROP_NO_DOC
import com.intellij.polySymbols.webTypes.impl.WebTypesJsonContributionAdapter
import com.intellij.polySymbols.webTypes.impl.wrap
import com.intellij.polySymbols.webTypes.json.GenericContribution
import com.intellij.polySymbols.webTypes.json.HtmlAttribute
import com.intellij.polySymbols.webTypes.json.HtmlAttributeValue
import com.intellij.polySymbols.webTypes.json.JsProperty
import com.intellij.polySymbols.webTypes.json.NamePatternRoot
import com.intellij.polySymbols.webTypes.json.attributeValue
import com.intellij.polySymbols.webTypes.json.evaluate
import com.intellij.polySymbols.webTypes.json.genericProperties
import com.intellij.polySymbols.webTypes.json.mapToTypeReferences
import com.intellij.polySymbols.webTypes.json.resolve
import com.intellij.polySymbols.webTypes.json.toApiStatus
import com.intellij.polySymbols.webTypes.json.toLangType
import com.intellij.polySymbols.webTypes.json.type
import com.intellij.polySymbols.webTypes.json.wrap
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

open class WebTypesSymbolBase : WebTypesSymbol {

  internal lateinit var base: WebTypesJsonContributionAdapter

  protected lateinit var queryExecutor: PolySymbolQueryExecutor

  private var _superContributions: List<PolySymbol>? = null

  private val superContributions: List<PolySymbol>
    get() = _superContributions
            ?: base.contribution.extends
              .also { _superContributions = emptyList() }
              ?.resolve(PolySymbolQueryStack(), queryExecutor, true, true)
              ?.toList()
              ?.also { contributions -> _superContributions = contributions }
            ?: emptyList()

  private val contributionProperties by lazy {
    base.contribution.genericProperties
  }

  @PolySymbol.Property(HtmlAttributeValueProperty::class)
  private val attributeValue by lazy {
    (base.contribution.attributeValue?.let { sequenceOf(HtmlAttributeValueImpl(it)) } ?: emptySequence())
      .plus(superContributions.asSequence().map { it.htmlAttributeValue })
      .merge()
  }

  @PolySymbol.Property(DocHideIconProperty::class)
  val docHideIcon: Boolean
    get() = icon == base.jsonOrigin.defaultIcon

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      PROP_TYPE_SUPPORT -> property.tryCast(base.jsonOrigin.typeSupport)
      base.jsonOrigin.typeSupport?.typeProperty -> {
        property.tryCast(
          (base.contribution.type)
            ?.let { base.jsonOrigin.typeSupport?.resolve(it.mapToTypeReferences()) }
          ?: superContributions.firstNotNullOfOrNull { it[property] }
        )
      }
      else -> property.tryCast(contributionProperties[property.name])
              ?: super[property]

    }

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    (symbol is WebTypesSymbolBase && symbol.base == this.base)
    || super.isEquivalentTo(symbol)

  override fun toString(): String =
    base.toString()

  override fun getModificationCount(): Long = 0

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

  internal fun init(webTypesJsonContributionAdapter: WebTypesJsonContributionAdapter, queryExecutor: PolySymbolQueryExecutor) {
    this.base = webTypesJsonContributionAdapter
    this.queryExecutor = queryExecutor
  }

  final override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    base.rootScope
      .getMatchingSymbols(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, stack)

  final override fun getSymbols(
    kind: PolySymbolKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    base.rootScope
      .getSymbols(base.contributionForQuery, base.jsonOrigin, kind, params)

  final override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    base.rootScope
      .getCodeCompletions(base.contributionForQuery, base.jsonOrigin, qualifiedName, params, stack)

  final override val kind: PolySymbolKind
    get() = base.kind

  @get:ApiStatus.Internal
  final override val origin: WebTypesJsonOrigin
    get() = base.jsonOrigin

  final override val name: String
    get() = if (base is WebTypesJsonContributionAdapter.Pattern) base.contributionName else base.name

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    if (this[PROP_NO_DOC] != true)
      PolySymbolDocumentationTarget.create(this, location, WebTypesSymbolDocumentationProvider(this))
    else
      null

  final override val icon: Icon?
    get() = base.icon
            ?: superContributions.asSequence().mapNotNull { it.icon }.firstOrNull()
            ?: base.jsonOrigin.defaultIcon

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

  final override val apiStatus: PolySymbolApiStatus
    get() = base.contribution.toApiStatus(base.jsonOrigin)

  final override val extension: Boolean
    get() = base.contribution.extension == true

  final override val priority: PolySymbol.Priority?
    get() = base.contribution.priority?.wrap()
            ?: superContributions.firstOrNull()?.priority

  override val modifiers: Set<PolySymbolModifier>
    get() = setOfNotNull(
      PolySymbolModifier.VIRTUAL.takeIf { base.contribution.virtual == true },
      PolySymbolModifier.ABSTRACT.takeIf { base.contribution.abstract == true },
      PolySymbolModifier.READONLY.takeIf { (base.contribution as? JsProperty)?.readOnly == true },
      when (required) {
        true -> PolySymbolModifier.REQUIRED
        false -> PolySymbolModifier.OPTIONAL
        null -> null
      }
    )

  private val required: Boolean?
    get() = (base.contribution as? GenericContribution)?.required
            ?: (base.contribution as? HtmlAttribute)?.required

  final override val queryScope: List<PolySymbolScope>
    get() = superContributions.asSequence()
      .flatMap { it.queryScope }
      .plus(this)
      .toList()

  final override fun isExclusiveFor(kind: PolySymbolKind): Boolean =
    base.isExclusiveFor(kind)
    || superContributions.flatMap { it.queryScope }.any { it.isExclusiveFor(kind) }

  override fun matchContext(context: PolyContext): Boolean =
    super.matchContext(context)
    && (base.jsonOrigin.framework == null || context.framework == null || base.jsonOrigin.framework == context.framework)
    && base.contribution.requiredContext.evaluate(context)

  internal class WebTypesSymbolWithPattern(private val jsonPattern: NamePatternRoot) : WebTypesSymbolBase(), PolySymbolWithPattern {
    override val pattern: PolySymbolPattern
      get() = jsonPattern.wrap(base.contribution.name, base.jsonOrigin)
  }

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

  private class WebTypesSymbolDocumentationProvider(private val symbol: WebTypesSymbolBase) :
    PolySymbolDocumentationProvider<WebTypesSymbolBase> {

    override fun loadIcon(path: String): Icon? =
      symbol.base.jsonOrigin.loadIcon(path)

    override fun createDocumentation(symbol: WebTypesSymbolBase, location: PsiElement?): PolySymbolDocumentation {
      val superContributionDocs = symbol.superContributions
        .mapNotNull { (it.getDocumentationTarget(location) as? PolySymbolDocumentationTarget)?.documentation }

      return PolySymbolDocumentation.builder(symbol, location)
        .description(
          symbol.base.contribution.description
            ?.let { symbol.base.jsonOrigin.renderDescription(symbol.base.contribution.description) }
          ?: superContributionDocs.firstNotNullOfOrNull { it.description }
        )
        .defaultValue(
          (symbol.base.contribution as? GenericContribution)?.default
          ?: (symbol.base.contribution as? HtmlAttribute)?.default
          ?: superContributionDocs.firstNotNullOfOrNull { it.defaultValue }
          ?: symbol.attributeValue?.default
        )
        .docUrl(symbol.base.contribution.docUrl
                ?: superContributionDocs.firstNotNullOfOrNull { it.docUrl }
        )
        .descriptionSections(
          (symbol.base.contribution.descriptionSections?.additionalProperties?.asSequence() ?: emptySequence())
            .plus(superContributionDocs.asSequence().flatMap {
              it.descriptionSections.asSequence()
            })
            .distinctBy { it.key }
            .associateBy({ it.key }, { symbol.base.jsonOrigin.renderDescription(it.value) })
        )
        .library(
          symbol.base.jsonOrigin.library?.let { lib ->
            lib + (symbol.base.jsonOrigin.version?.takeIf { it != "0.0.0" }?.let { "@$it" } ?: "")
          }
        )
        .build()
    }

  }
}