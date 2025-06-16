// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.webTypes.WebTypesJsonOrigin
import com.intellij.polySymbols.webTypes.WebTypesScopeBase
import com.intellij.polySymbols.webTypes.WebTypesSymbolBase
import com.intellij.polySymbols.webTypes.json.*
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

abstract class WebTypesJsonContributionAdapter private constructor(
  internal val contribution: BaseContribution,
  internal val jsonOrigin: WebTypesJsonOrigin,
  internal val cacheHolder: UserDataHolderEx,
  internal val rootScope: WebTypesScopeBase,
  override val qualifiedKind: PolySymbolQualifiedKind,
) :
  StaticPolySymbolScopeBase.StaticSymbolContributionAdapter {


  companion object {
    fun BaseContribution.wrap(
      origin: WebTypesJsonOrigin,
      rootScope: WebTypesScopeBase,
      qualifiedKind: PolySymbolQualifiedKind,
    ): WebTypesJsonContributionAdapter =
      if (pattern != null) {
        Pattern(this, origin, UserDataHolderBase(), rootScope, qualifiedKind)
      }
      else if ((name != null && name.startsWith(VUE_DIRECTIVE_PREFIX))
               && origin.framework == VUE_FRAMEWORK
               && qualifiedKind == HTML_ATTRIBUTES) {
        LegacyVueDirective(this, origin, UserDataHolderBase(), rootScope)
      }
      else if (name != null && qualifiedKind == HTML_VUE_LEGACY_COMPONENTS && this is HtmlElement) {
        LegacyVueComponent(this, origin, UserDataHolderBase(), rootScope)
      }
      else {
        Static(this, origin, UserDataHolderBase(), rootScope, qualifiedKind)
      }
  }

  override val framework: String? get() = jsonOrigin.framework

  val icon: Icon? get() = contribution.icon?.let { IconLoader.createLazy { jsonOrigin.loadIcon(it) ?: EmptyIcon.ICON_0 } }

  abstract override val name: String
  open val contributionName: String = contribution.name ?: "<no-name>"
  abstract val jsonPattern: NamePatternRoot?

  override val pattern: PolySymbolPattern?
    get() = jsonPattern?.wrap(contribution.name, jsonOrigin)

  open val contributionForQuery: GenericContributionsHost get() = contribution

  private var exclusiveContributions: Set<PolySymbolQualifiedKind>? = null

  override fun matchContext(context: PolyContext): Boolean =
    super.matchContext(context) && contribution.requiredContext.evaluate(context)

  fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    (exclusiveContributions
     ?: when {
       contribution.exclusiveContributions.isEmpty() -> emptySet()
       else -> contribution.exclusiveContributions
         .asSequence()
         .mapNotNull { path ->
           if (!path.startsWith('/')) return@mapNotNull null
           val slash = path.indexOf('/', 1)
           if (path.lastIndexOf('/') != slash) return@mapNotNull null
           val n = path.substring(1, slash).asWebTypesSymbolNamespace()
                   ?: return@mapNotNull null
           val k = path.substring(slash + 1, path.length)
           PolySymbolQualifiedKind[n, k]
         }
         .toSet()
     }.also { exclusiveContributions = it }
    ).contains(qualifiedKind)

  override fun withQueryExecutorContext(queryExecutor: PolySymbolQueryExecutor): PolySymbol =
    (this.contribution.pattern?.let { WebTypesSymbolBase.WebTypesSymbolWithPattern(it) }
     ?: WebTypesSymbolFactoryEP.get(qualifiedKind)?.create()
     ?: WebTypesSymbolBase()
    ).also { it.init(this, queryExecutor) }

  abstract fun createPointer(): Pointer<out WebTypesJsonContributionAdapter>

  override fun equals(other: Any?): Boolean =
    other === this
    || other is WebTypesJsonContributionAdapter
    && other.javaClass == javaClass
    && other.contribution === contribution
    && other.jsonOrigin === jsonOrigin
    && other.rootScope === rootScope
    && other.qualifiedKind === qualifiedKind

  override fun hashCode(): Int {
    var result = contribution.hashCode()
    result = 31 * result + jsonOrigin.hashCode()
    result = 31 * result + rootScope.hashCode()
    result = 31 * result + qualifiedKind.hashCode()
    return result
  }

  private class Static(
    contribution: BaseContribution,
    context: WebTypesJsonOrigin,
    cacheHolder: UserDataHolderEx,
    rootScope: WebTypesScopeBase,
    qualifiedKind: PolySymbolQualifiedKind,
  ) : WebTypesJsonContributionAdapter(contribution, context, cacheHolder, rootScope, qualifiedKind) {

    override val name: String
      get() = contribution.name ?: "<no-name>"

    override fun toString(): String =
      "$qualifiedKind/$name <static>"

    override val jsonPattern: NamePatternRoot? get() = null

    override fun createPointer(): Pointer<Static> =
      object : WebTypesJsonContributionAdapterPointer<Static>(this) {
        override fun dereference(): Static? =
          rootScope.dereference()?.let {
            Static(contribution, jsonContext, cacheHolder, it, qualifiedKind)
          }

      }

  }

  internal class Pattern(
    contribution: BaseContribution,
    context: WebTypesJsonOrigin,
    cacheHolder: UserDataHolderEx,
    rootScope: WebTypesScopeBase,
    qualifiedKind: PolySymbolQualifiedKind,
  ) : WebTypesJsonContributionAdapter(contribution, context, cacheHolder, rootScope, qualifiedKind) {

    override val jsonPattern: NamePatternRoot?
      get() = contribution.pattern

    override val name: String = "<pattern>"

    override fun toString(): String =
      "$qualifiedKind/${jsonPattern?.wrap("", jsonOrigin)?.getStaticPrefixes()?.toSet() ?: "[]"}... <pattern>"

    override fun createPointer(): Pointer<Pattern> =
      object : WebTypesJsonContributionAdapterPointer<Pattern>(this) {

        override fun dereference(): Pattern? =
          rootScope.dereference()?.let {
            Pattern(contribution, jsonContext, cacheHolder, it, qualifiedKind)
          }

      }

  }

  private class LegacyVueDirective(
    contribution: BaseContribution,
    context: WebTypesJsonOrigin,
    cacheHolder: UserDataHolderEx,
    rootScope: WebTypesScopeBase,
  ) : WebTypesJsonContributionAdapter(contribution, context, cacheHolder, rootScope, HTML_VUE_DIRECTIVES) {

    override val name: String =
      contribution.name.substring(2)

    override val contributionName: String
      get() = name

    override fun toString(): String =
      "$qualifiedKind/${this.name} <static-legacy>"

    override val jsonPattern: NamePatternRoot? get() = null

    override fun createPointer(): Pointer<LegacyVueDirective> =
      object : WebTypesJsonContributionAdapterPointer<LegacyVueDirective>(this) {

        override fun dereference(): LegacyVueDirective? =
          rootScope.dereference()?.let {
            LegacyVueDirective(contribution, jsonContext, cacheHolder, it)
          }

      }
  }

  private class LegacyVueComponent(
    contribution: HtmlElement,
    context: WebTypesJsonOrigin,
    cacheHolder: UserDataHolderEx,
    rootScope: WebTypesScopeBase,
  ) : WebTypesJsonContributionAdapter(contribution, context, cacheHolder, rootScope, HTML_VUE_COMPONENTS) {

    private var _contributionForQuery: GenericContributionsHost? = null

    override val name: String = contribution.name.let {
      if (it.contains('-'))
        toVueComponentPascalName(contribution.name)
      else it
    }

    override fun toString(): String =
      "$qualifiedKind/$name <legacy static>"

    override val jsonPattern: NamePatternRoot? get() = null

    override val contributionForQuery: GenericContributionsHost
      get() = _contributionForQuery
              ?: (contribution as HtmlElement).convertToComponentContribution().also { _contributionForQuery = it }

    override fun createPointer(): Pointer<LegacyVueComponent> =
      object : WebTypesJsonContributionAdapterPointer<LegacyVueComponent>(this) {

        override fun dereference(): LegacyVueComponent? =
          rootScope.dereference()?.let {
            LegacyVueComponent(contribution as HtmlElement, jsonContext, cacheHolder, it)
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
        map[HTML_VUE_COMPONENT_PROPS.kind] = GenericHtmlContributions().also { contributions ->
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

  private abstract class WebTypesJsonContributionAdapterPointer<T : WebTypesJsonContributionAdapter>(wrapper: T) : Pointer<T> {

    val contribution = wrapper.contribution
    val jsonContext = wrapper.jsonOrigin
    val cacheHolder = wrapper.cacheHolder
    val rootScope = wrapper.rootScope.createPointer()
    val qualifiedKind = wrapper.qualifiedKind

  }
}