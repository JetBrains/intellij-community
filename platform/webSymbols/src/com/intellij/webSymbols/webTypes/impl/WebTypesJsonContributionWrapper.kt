// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EmptyIcon
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Priority
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorImpl.Companion.asSymbolNamespace
import com.intellij.webSymbols.utils.merge
import com.intellij.webSymbols.webTypes.WebTypesJsonOrigin
import com.intellij.webSymbols.webTypes.WebTypesSymbol
import com.intellij.webSymbols.webTypes.WebTypesSymbolsScopeBase
import com.intellij.webSymbols.webTypes.json.*
import javax.swing.Icon

internal abstract class WebTypesJsonContributionWrapper private constructor(protected val contribution: BaseContribution,
                                                                            private val jsonOrigin: WebTypesJsonOrigin,
                                                                            protected val cacheHolder: UserDataHolderEx,
                                                                            protected val rootScope: WebTypesSymbolsScopeBase,
                                                                            val namespace: SymbolNamespace,
                                                                            val kind: String) {


  companion object {
    fun BaseContribution.wrap(origin: WebTypesJsonOrigin,
                              rootScope: WebTypesSymbolsScopeBase,
                              root: SymbolNamespace,
                              kind: SymbolKind): WebTypesJsonContributionWrapper =
      if (pattern != null) {
        Pattern(this, origin, UserDataHolderBase(), rootScope, root, kind)
      }
      else if ((name != null && name.startsWith(VUE_DIRECTIVE_PREFIX))
               && origin.framework == VUE_FRAMEWORK
               && kind == KIND_HTML_ATTRIBUTES) {
        LegacyVueDirective(this, origin, UserDataHolderBase(), rootScope, root)
      }
      else if (name != null && kind == KIND_HTML_VUE_LEGACY_COMPONENTS && this is HtmlElement) {
        LegacyVueComponent(this, origin, UserDataHolderBase(), rootScope, root)
      }
      else {
        Static(this, origin, UserDataHolderBase(), rootScope, root, kind)
      }
  }

  val framework: String? get() = jsonOrigin.framework

  val icon get() = contribution.icon?.let { IconLoader.createLazy { jsonOrigin.loadIcon(it) ?: EmptyIcon.ICON_0 } }

  abstract val name: String
  open val contributionName: String = contribution.name ?: "<no-name>"
  abstract val jsonPattern: NamePatternRoot?

  val pattern: WebSymbolsPattern?
    get() = jsonPattern?.wrap(contribution.name)
  open val contributionForQuery: GenericContributionsHost get() = contribution

  private var exclusiveContributions: Set<Pair<SymbolNamespace, String>>? = null

  fun isExclusiveFor(namespace: SymbolNamespace, kind: SymbolKind): Boolean =
    (exclusiveContributions
     ?: when {
       contribution.exclusiveContributions.isEmpty() -> emptySet()
       else -> contribution.exclusiveContributions
         .asSequence()
         .mapNotNull { path ->
           if (!path.startsWith('/')) return@mapNotNull null
           val slash = path.indexOf('/', 1)
           if (path.lastIndexOf('/') != slash) return@mapNotNull null
           val n = path.substring(1, slash).asSymbolNamespace()
                   ?: return@mapNotNull null
           val k = path.substring(slash + 1, path.length)
           Pair(n, k)
         }
         .toSet()
     }.also { exclusiveContributions = it }
    )
      .takeIf { it.isNotEmpty() }
      ?.contains(Pair(namespace, kind)) == true

  fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): WebTypesSymbolImpl =
    WebTypesSymbolImpl(this, queryExecutor)

  internal class WebTypesSymbolImpl(private val base: WebTypesJsonContributionWrapper,
                                    private val queryExecutor: WebSymbolsQueryExecutor)
    : WebTypesSymbol {

    private var _superContributions: List<WebSymbol>? = null

    private val superContributions: List<WebSymbol>
      get() = _superContributions
              ?: base.contribution.extends
                .also { _superContributions = emptyList() }
                ?.resolve(null, listOf(), queryExecutor, true, true)
                ?.toList()
                ?.also { contributions -> _superContributions = contributions }
              ?: emptyList()

    override fun getSymbols(namespace: SymbolNamespace?,
                            kind: String,
                            name: String?,
                            params: WebSymbolsNameMatchQueryParams,
                            scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
      base.rootScope
        .getSymbols(base.contributionForQuery, this.namespace, base.jsonOrigin,
                    namespace, kind, name, params, scope)
        .toList()

    override fun getCodeCompletions(namespace: SymbolNamespace?,
                                    kind: String,
                                    name: String?,
                                    params: WebSymbolsCodeCompletionQueryParams,
                                    scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
      base.rootScope
        .getCodeCompletions(base.contributionForQuery, this.namespace, base.jsonOrigin,
                            namespace, kind, name, params, scope)
        .toList()

    override val kind: SymbolKind
      get() = base.kind

    override val origin: WebTypesJsonOrigin
      get() = base.jsonOrigin

    override val namespace: SymbolNamespace
      get() = base.namespace

    override val name: String
      get() = if (base is Pattern) base.contributionName else base.name

    override val description: String?
      get() = base.contribution.description
                ?.let { base.jsonOrigin.renderDescription(base.contribution.description) }
              ?: superContributions.asSequence().mapNotNull { it.description }.firstOrNull()

    override val descriptionSections: Map<String, String>
      get() = (base.contribution.descriptionSections?.additionalProperties?.asSequence() ?: emptySequence())
        .plus(superContributions.asSequence().flatMap { it.descriptionSections.asSequence() })
        .distinctBy { it.key }
        .associateBy({ it.key }, { it.value })

    override val docUrl: String?
      get() = base.contribution.docUrl
              ?: superContributions.asSequence().mapNotNull { it.docUrl }.firstOrNull()

    override val icon: Icon?
      get() = base.icon ?: superContributions.asSequence().mapNotNull { it.icon }.firstOrNull()

    override val location: WebTypesSymbol.Location?
      // Should not reach to super contributions, because it can lead to stack overflow
      // when special containers are trying to merge symbols
      get() = base.contribution.source
        ?.let {
          base.jsonOrigin.resolveSourceLocation(it)
        }

    override val source: PsiElement?
      // Should not reach to super contributions, because it can lead to stack overflow
      // when special containers are trying to merge symbols
      get() = base.contribution.source
        ?.let {
          base.jsonOrigin.resolveSourceSymbol(it, base.cacheHolder)
        }

    override val attributeValue: WebSymbolHtmlAttributeValue?
      get() = (base.contribution.attributeValue?.let { sequenceOf(HtmlAttributeValueImpl(it)) } ?: emptySequence())
        .plus(superContributions.asSequence().map { it.attributeValue })
        .merge()

    override val type: Any?
      get() = (base.contribution.type)
                ?.let { base.jsonOrigin.typeSupport?.resolve(it.mapToTypeReferences()) }
              ?: superContributions.asSequence().mapNotNull { it.type }.firstOrNull()

    override val deprecated: Boolean
      get() = base.contribution.deprecated == true

    override val experimental: Boolean
      get() = base.contribution.experimental == true

    override val virtual: Boolean
      get() = base.contribution.virtual == true

    override val extension: Boolean
      get() = base.contribution.extension == true

    override val priority: Priority?
      get() = base.contribution.priority?.wrap()
              ?: superContributions.firstOrNull()?.priority

    override val proximity: Int?
      get() = base.contribution.proximity
              ?: superContributions.firstOrNull()?.proximity

    override val abstract: Boolean
      get() = base.contribution.abstract == true

    override val required: Boolean?
      get() = (base.contribution as? GenericContribution)?.required
              ?: (base.contribution as? HtmlAttribute)?.required
              ?: superContributions.firstOrNull()?.required

    override val defaultValue: String?
      get() = (base.contribution as? GenericContribution)?.default
              ?: (base.contribution as? HtmlAttribute)?.default
              ?: superContributions.firstOrNull()?.defaultValue

    override val pattern: WebSymbolsPattern?
      get() = base.jsonPattern?.wrap(base.contribution.name)

    override fun createPointer(): Pointer<WebTypesSymbolImpl> {
      val queryExecutorPtr = this.queryExecutor.createPointer()
      val basePtr = this.base.createPointer()
      return Pointer<WebTypesSymbolImpl> {
        val queryExecutor = queryExecutorPtr.dereference() ?: return@Pointer null
        val base = basePtr.dereference() ?: return@Pointer null
        base.withQueryExecutorContext(queryExecutor)
      }
    }

    override val queryScope: Sequence<WebSymbolsScope>
      get() = superContributions.asSequence()
        .flatMap { it.queryScope }
        .plus(this)

    override val properties: Map<String, Any>
      get() = base.contribution.genericProperties

    override fun isExclusiveFor(namespace: SymbolNamespace?, kind: SymbolKind): Boolean =
      namespace != null
      && namespace == this.namespace
      && (base.isExclusiveFor(namespace, kind)
          || superContributions.any { it.isExclusiveFor(namespace, kind) })

    override fun toString(): String =
      base.toString()

    override fun isEquivalentTo(symbol: Symbol): Boolean =
      (symbol is WebTypesSymbolImpl && symbol.base == this.base)
      || super.isEquivalentTo(symbol)

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

  abstract fun createPointer(): Pointer<out WebTypesJsonContributionWrapper>

  private class Static(contribution: BaseContribution,
                       context: WebTypesJsonOrigin,
                       cacheHolder: UserDataHolderEx,
                       rootScope: WebTypesSymbolsScopeBase,
                       namespace: SymbolNamespace,
                       kind: String) : WebTypesJsonContributionWrapper(contribution, context, cacheHolder, rootScope, namespace, kind) {

    override val name: String
      get() = contribution.name ?: "<no-name>"

    override fun toString(): String =
      "$kind/$name <static>"

    override val jsonPattern: NamePatternRoot? get() = null

    override fun createPointer(): Pointer<Static> =
      object : WebTypesJsonContributionWrapperPointer<Static>(this) {
        override fun dereference(): Static? =
          rootScope.dereference()?.let {
            Static(contribution, jsonContext, cacheHolder, it, namespace, kind)
          }

      }

  }

  private class Pattern(contribution: BaseContribution,
                        context: WebTypesJsonOrigin,
                        cacheHolder: UserDataHolderEx,
                        rootScope: WebTypesSymbolsScopeBase,
                        namespace: SymbolNamespace,
                        kind: String)
    : WebTypesJsonContributionWrapper(contribution, context, cacheHolder, rootScope, namespace, kind) {

    override val jsonPattern: NamePatternRoot?
      get() = contribution.pattern

    override val name: String = "<pattern>"

    override fun toString(): String =
      "$kind/${jsonPattern?.wrap("")?.getStaticPrefixes()?.toSet() ?: "[]"}... <pattern>"

    override fun createPointer(): Pointer<Pattern> =
      object : WebTypesJsonContributionWrapperPointer<Pattern>(this) {

        override fun dereference(): Pattern? =
          rootScope.dereference()?.let {
            Pattern(contribution, jsonContext, cacheHolder, it, namespace, kind)
          }

      }

  }

  private class LegacyVueDirective(contribution: BaseContribution,
                                   context: WebTypesJsonOrigin,
                                   cacheHolder: UserDataHolderEx,
                                   rootScope: WebTypesSymbolsScopeBase,
                                   root: SymbolNamespace)
    : WebTypesJsonContributionWrapper(contribution, context, cacheHolder, rootScope, root, KIND_HTML_VUE_DIRECTIVES) {

    override val name: String =
      contribution.name.substring(2)

    override val contributionName: String
      get() = name

    override fun toString(): String =
      "$kind/${this.name} <static-legacy>"

    override val jsonPattern: NamePatternRoot? get() = null

    override fun createPointer(): Pointer<LegacyVueDirective> =
      object : WebTypesJsonContributionWrapperPointer<LegacyVueDirective>(this) {

        override fun dereference(): LegacyVueDirective? =
          rootScope.dereference()?.let {
            LegacyVueDirective(contribution, jsonContext, cacheHolder, it, namespace)
          }

      }
  }

  private class LegacyVueComponent(contribution: HtmlElement,
                                   context: WebTypesJsonOrigin,
                                   cacheHolder: UserDataHolderEx,
                                   rootScope: WebTypesSymbolsScopeBase,
                                   root: SymbolNamespace)
    : WebTypesJsonContributionWrapper(contribution, context, cacheHolder, rootScope, root, KIND_HTML_VUE_COMPONENTS) {

    private var _contributionForQuery: GenericContributionsHost? = null

    override val name: String = contribution.name.let {
      if (it.contains('-'))
        toVueComponentPascalName(contribution.name)
      else it
    }

    override fun toString(): String =
      "$kind/$name <legacy static>"

    override val jsonPattern: NamePatternRoot? get() = null

    override val contributionForQuery: GenericContributionsHost
      get() = _contributionForQuery
              ?: (contribution as HtmlElement).convertToComponentContribution().also { _contributionForQuery = it }

    override fun createPointer(): Pointer<LegacyVueComponent> =
      object : WebTypesJsonContributionWrapperPointer<LegacyVueComponent>(this) {

        override fun dereference(): LegacyVueComponent? =
          rootScope.dereference()?.let {
            LegacyVueComponent(contribution as HtmlElement, jsonContext, cacheHolder, it, namespace)
          }

      }

    companion object {

      private fun toVueComponentPascalName(name: String): String {
        val result = StringBuilder()
        var nextCapitalized = true
        for (ch in name) {
          when {
            ch == '-' -> nextCapitalized = true
            nextCapitalized -> {
              result.append(StringUtil.toUpperCase(ch))
              nextCapitalized = false
            }
            else -> result.append(ch)
          }
        }
        return result.toString()
      }

      fun HtmlElement.convertToComponentContribution(): GenericContributionsHost {
        val result = GenericHtmlContribution()
        val map = result.additionalProperties
        map.putAll(this.additionalProperties)
        val scopedSlots = result.genericContributions["vue-scoped-slots"]
        if (!scopedSlots.isNullOrEmpty()) {
          scopedSlots.mapTo(map.computeIfAbsent("slots") { GenericHtmlContributions() }) { contribution ->
            GenericHtmlContributionOrProperty().also { it.value = contribution.convertToSlot() }
          }
          map.remove("vue-scoped-slots")
        }
        map[KIND_HTML_VUE_COMPONENT_PROPS] = GenericHtmlContributions().also { contributions ->
          this.attributes.mapTo(contributions) { attribute ->
            GenericHtmlContributionOrProperty().also { it.value = attribute.convertToPropsContribution() }
          }
        }
        result.events.addAll(this.events)
        return result
      }

      fun HtmlAttribute.convertToPropsContribution(): GenericContribution {
        val result = GenericHtmlContribution()
        result.copyLegacyFrom(this)
        result.required = this.required
        if (this.attributeValue != null || this.default != null) {
          result.attributeValue =
            HtmlAttributeValue().also {
              attributeValue?.let { other ->
                it.required = other.required
                it.default = other.default
                it.type = other.type
              }
              if (it.default == null) {
                it.default = this.default
              }
            }
        }
        return result
      }

      fun GenericContribution.convertToSlot(): GenericHtmlContribution {
        val result = GenericHtmlContribution()
        result.copyLegacyFrom(this)
        result.additionalProperties["vue-properties"] = result.additionalProperties["properties"]
        return result
      }

    }
  }

  private abstract class WebTypesJsonContributionWrapperPointer<T : WebTypesJsonContributionWrapper>(wrapper: T) : Pointer<T> {

    val contribution = wrapper.contribution
    val jsonContext = wrapper.jsonOrigin
    val cacheHolder = wrapper.cacheHolder
    val rootScope = wrapper.rootScope.createPointer()
    val namespace = wrapper.namespace
    val kind = wrapper.kind

  }
}