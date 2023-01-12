// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EmptyIcon
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.DisablementRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.EnablementRules
import com.intellij.webSymbols.context.WebSymbolsContextRulesProvider
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.query.impl.SearchMap
import com.intellij.webSymbols.utils.HtmlMarkdownUtils
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper.Companion.wrap
import com.intellij.webSymbols.webTypes.impl.wrap
import com.intellij.webSymbols.webTypes.json.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@Internal
abstract class WebTypesSymbolsScopeBase : WebSymbolsScope, WebSymbolsContextRulesProvider {

  private val namesProviderCache: MutableMap<WebSymbolNamesProvider, NameProvidersCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var namesProviderCacheMisses = 0

  private val queryExecutorCache: MutableMap<WebSymbolsQueryExecutor, QueryExecutorContributionsCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var queryExecutorCacheMisses = 0

  private val roots = mutableMapOf<Contributions, WebTypesJsonOrigin>()

  private val frameworkConfigs = mutableMapOf<WebTypes, FrameworkConfig>()
  private val contextsConfigs = mutableMapOf<WebTypes, ContextsConfig>()

  private val contextRulesCache = createContextRulesCache()

  private val nameConversionRulesCache = createNameConversionRulesCache()

  @JvmField
  protected var modCount: Long = 0

  abstract override fun createPointer(): Pointer<out WebTypesSymbolsScopeBase>

  fun getNameConversionRulesProvider(framework: FrameworkId): WebSymbolNameConversionRulesProvider =
    WebTypesSymbolNameConversionRulesProvider(framework, this)

  override fun getModificationCount(): Long =
    modCount

  final override fun getSymbols(namespace: SymbolNamespace?,
                                kind: String,
                                name: String?,
                                params: WebSymbolsNameMatchQueryParams,
                                scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    if (namespace != null) {
      getMaps(params).flatMap {
        it.getSymbols(namespace, kind, name, params, Stack(scope))
      }.toList()
    }
    else emptyList()


  final override fun getCodeCompletions(namespace: SymbolNamespace?,
                                        kind: String,
                                        name: String?,
                                        params: WebSymbolsCodeCompletionQueryParams,
                                        scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    if (namespace != null) {
      getMaps(params).flatMap {
        it.getCodeCompletions(namespace, kind, name, params, Stack(scope))
      }.toList()
    }
    else emptyList()

  override fun getContextRules(): MultiMap<ContextKind, WebSymbolsContextKindRules> =
    contextRulesCache.value

  internal fun getSymbols(host: GenericContributionsHost,
                          defaultNamespace: SymbolNamespace,
                          origin: WebTypesJsonOrigin,
                          namespace: SymbolNamespace?,
                          kind: String,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          scopeStack: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    getMap(params.queryExecutor, host, origin)
      .getSymbols(namespace ?: defaultNamespace, kind, name, params, scopeStack)
      .toList()

  internal fun getCodeCompletions(host: GenericContributionsHost,
                                  defaultNamespace: SymbolNamespace,
                                  origin: WebTypesJsonOrigin,
                                  namespace: SymbolNamespace?,
                                  kind: String,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scopeStack: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    getMap(params.queryExecutor, host, origin)
      .getCodeCompletions(namespace ?: defaultNamespace, kind, name, params, scopeStack)
      .toList()

  protected fun addWebTypes(webTypes: WebTypes, context: WebTypesJsonOrigin) {
    modCount++

    addRoot(webTypes.contributions, context)

    val framework = context.framework
    var dropCaches = false
    if (framework != null) {
      webTypes.frameworkConfig?.let {
        frameworkConfigs[webTypes] = it
        dropCaches = true
      }
    }
    webTypes.contextsConfig?.let {
      contextsConfigs[webTypes] = it
      dropCaches = true
    }
    if (dropCaches) dropCaches()
  }

  protected fun removeWebTypes(webTypes: WebTypes) {
    modCount++

    removeRoot(webTypes.contributions)

    var dropCaches = false
    if (frameworkConfigs.remove(webTypes) != null) {
      dropCaches = true
    }
    if (contextsConfigs.remove(webTypes) != null) {
      dropCaches = true
    }
    if (dropCaches) {
      dropCaches()
    }
  }

  private fun dropCaches() {
    namesProviderCache.clear()
    queryExecutorCache.clear()
    contextRulesCache.drop()
    nameConversionRulesCache.drop()
  }

  private fun getMaps(params: WebSymbolsQueryParams): Sequence<ContributionSearchMap> =
    roots.asSequence()
      .filter { (_, origin) -> origin.matchContext(params.queryExecutor.context) }
      .map { (contributions, origin) ->
        getMap(params.queryExecutor, contributions, origin)
      }

  private fun getMap(queryExecutor: WebSymbolsQueryExecutor,
                     host: GenericContributionsHost,
                     origin: WebTypesJsonOrigin): ContributionSearchMap =
    getOrCreateMap(queryExecutor, host) { consumer ->
      host.getAllContributions(origin.framework)
        .forEach { (namespace, kind, list) ->
          list.forEach { consumer(it.wrap(origin, this@WebTypesSymbolsScopeBase, namespace, kind)) }
        }
    }


  private fun getMap(queryExecutor: WebSymbolsQueryExecutor,
                     root: Contributions,
                     origin: WebTypesJsonOrigin): ContributionSearchMap =
    getOrCreateMap(queryExecutor, root) { consumer ->
      root.getAllContributions(origin.framework)
        .forEach { (namespace, kind, list) ->
          list.asSequence()
            .map { it.wrap(origin, this@WebTypesSymbolsScopeBase, namespace, kind) }
            .forEach(consumer)
        }
    }

  private fun getOrCreateMap(queryExecutor: WebSymbolsQueryExecutor,
                             key: Any,
                             mapInitializer: (consumer: (WebTypesJsonContributionWrapper) -> Unit) -> Unit): ContributionSearchMap =
    getNameProvidersCache(queryExecutor.namesProvider).getOrCreateMap(key, mapInitializer)

  private fun getNameProvidersCache(namesProvider: WebSymbolNamesProvider): NameProvidersCache {
    if (namesProviderCacheMisses > 100) {
      // Get rid of old soft keys
      namesProviderCacheMisses = 0
      namesProviderCache.clear()
    }
    return namesProviderCache.computeIfAbsent(namesProvider) {
      namesProviderCacheMisses++; NameProvidersCache(namesProvider)
    }
      .also { it.checkForModifications() }
  }

  private fun getQueryExecutorContributionsCache(queryExecutor: WebSymbolsQueryExecutor): QueryExecutorContributionsCache {
    if (queryExecutorCacheMisses > 100) {
      // Get rid of old soft keys
      queryExecutorCacheMisses = 0
      queryExecutorCache.clear()
    }
    return queryExecutorCache.computeIfAbsent(queryExecutor) { queryExecutorCacheMisses++; QueryExecutorContributionsCache(queryExecutor) }
      .also { it.checkForModifications() }
  }

  private fun addRoot(root: Contributions?, context: WebTypesJsonOrigin) {
    if (root == null) return
    roots[root] = context
  }

  private fun removeRoot(root: Contributions?) {
    roots.remove(root)
  }

  private fun createContextRulesCache(): ClearableLazyValue<MultiMap<ContextKind, WebSymbolsContextKindRules>> =
    ClearableLazyValue.create {
      data class RulesEntry(val kind: ContextKind,
                            val name: ContextName,
                            val enablementRules: EnablementRules?,
                            val disablementRules: DisablementRules?)

      val rulesPerKind = contextsConfigs.values.asSequence()
        .flatMap { it.additionalProperties.entries }
        .filter { (name, config) -> name != null && config.kind != null }
        .map { (name, config) -> RulesEntry(config.kind, name, config.enableWhen?.wrap(), config.disableWhen?.wrap()) }
        .plus(
          frameworkConfigs
            .filter { (webTypes, _) -> webTypes.framework != null }
            .map { (webTypes, config) ->
              RulesEntry(KIND_FRAMEWORK, webTypes.framework, config.enableWhen?.wrap(), config.disableWhen?.wrap())
            }
        )
        .groupBy { it.kind }

      val result = MultiMap.create<ContextKind, WebSymbolsContextKindRules>()
      rulesPerKind.forEach { (kind, rules) ->
        val rulesPerName = rules.groupBy { it.name }
        val enablementRules = rulesPerName.mapValues { (_, entries) -> entries.mapNotNull { it.enablementRules } }
        val disablementRules = rulesPerName.mapValues { (_, entries) -> entries.mapNotNull { it.disablementRules } }
        result.putValue(kind, WebSymbolsContextKindRules.create(enablementRules, disablementRules))
      }
      result
    }

  private fun <T> createEnablementCache(accessor: (config: FrameworkConfig) -> T?,
                                        accessor2: (config: ContextConfig) -> T?): ClearableLazyValue<Map<String, Map<String, List<T>>>> =
    ClearableLazyValue.create {
      val result = mutableMapOf<String, MutableMap<String, MutableList<T>>>()
      for (entry in contextsConfigs) {
        for ((name, config) in entry.value.additionalProperties) {
          val kind = config.kind ?: continue
          val rules = accessor2(config) ?: continue
          result
            .getOrPut(kind) { mutableMapOf() }
            .getOrPut(name) { mutableListOf() }
            .add(rules)
        }
      }
      for ((webTypes, config) in frameworkConfigs) {
        val framework = webTypes.framework ?: continue
        val rules = accessor(config) ?: continue
        result
          .getOrPut(KIND_FRAMEWORK) { mutableMapOf() }
          .getOrPut(framework) { mutableListOf() }
          .add(rules)
      }
      // Make the map not mutable
      result.mapValues { (_, map) -> map.mapValues { (_, innerMap) -> innerMap.toList() } }
    }

  private fun createNameConversionRulesCache(): ClearableLazyValue<Map<FrameworkId, WebSymbolNameConversionRules>> =
    ClearableLazyValue.create {
      frameworkConfigs
        .asSequence()
        .mapNotNull { (webTypes, config) ->
          val framework = webTypes.framework ?: return@mapNotNull null
          val builder = WebSymbolNameConversionRules.builder()

          buildNameConverters(config.canonicalNames?.additionalProperties, { mergeConverters(listOf(it)) }, builder::addCanonicalNamesRule)
          buildNameConverters(config.matchNames?.additionalProperties, { mergeConverters(it) }, builder::addMatchNamesRule)
          buildNameConverters(config.nameVariants?.additionalProperties, { mergeConverters(it) }, builder::addNameVariantsRule)

          Pair(framework, builder.build())
        }
        .toMap()
    }

  private inner class ContributionSearchMap(namesProvider: WebSymbolNamesProvider)
    : SearchMap<WebTypesJsonContributionWrapper>(namesProvider) {

    fun add(item: WebTypesJsonContributionWrapper) {
      add(item.namespace, item.kind, item.name, item.jsonPattern?.wrap(null), item)
    }

    override fun Sequence<WebTypesJsonContributionWrapper>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<WebSymbol> {
      val cache = getQueryExecutorContributionsCache(params.queryExecutor)
      return filter { params.framework == null || it.framework == null || it.framework == params.framework }
        .map { cache.getOrCreateSymbol(it) }
    }

  }

  private inner class NameProvidersCache(private val namesProvider: WebSymbolNamesProvider) {
    private val mapsCache: MutableMap<Any, ContributionSearchMap> = ConcurrentHashMap()
    private var namesProviderTimestamp: Long = -1

    fun getOrCreateMap(key: Any,
                       mapInitializer: (consumer: (WebTypesJsonContributionWrapper) -> Unit) -> Unit): ContributionSearchMap =
      mapsCache.getOrPut(key) {
        ContributionSearchMap(namesProvider)
          .also { mapInitializer(it::add) }
      }

    fun checkForModifications() {
      if (namesProvider.modificationCount != this.namesProviderTimestamp) {
        synchronized(this) {
          if (namesProvider.modificationCount != this.namesProviderTimestamp) {
            mapsCache.clear()
            this.namesProviderTimestamp = namesProvider.modificationCount
          }
        }
      }
    }

  }

  private inner class QueryExecutorContributionsCache(private val queryExecutor: WebSymbolsQueryExecutor) {
    private val symbolsCache: MutableMap<WebTypesJsonContributionWrapper, WebSymbol> = ConcurrentHashMap()
    private var queryExecutorModificationCount: Long = -1

    fun getOrCreateSymbol(item: WebTypesJsonContributionWrapper): WebSymbol =
      symbolsCache.getOrPut(item) { item.withQueryExecutorContext(queryExecutor) }

    fun checkForModifications() {
      if (queryExecutor.modificationCount != this.queryExecutorModificationCount) {
        synchronized(this) {
          if (queryExecutor.modificationCount != this.queryExecutorModificationCount) {
            symbolsCache.clear()
            this.queryExecutorModificationCount = queryExecutor.modificationCount
          }
        }
      }
    }

  }

  protected class WebTypesJsonOriginImpl(
    webTypes: WebTypes,
    override val typeSupport: WebTypesSymbolTypeSupport,
    private val symbolLocationResolver: (source: SourceBase) -> WebTypesSymbol.Location? = { null },
    private val sourceSymbolResolver: (location: WebTypesSymbol.Location, cacheHolder: UserDataHolderEx) -> PsiElement? = { _, _ -> null },
    private val iconLoader: (path: String) -> Icon? = { null },
    override val version: String? = webTypes.version
  ) : WebTypesJsonOrigin {

    override val framework: FrameworkId? = webTypes.framework
    override val library: String? = webTypes.name
    private val contextExpr = webTypes.context

    private val descriptionRenderer: (String) -> String? =
      when (webTypes.descriptionMarkupWithLegacy) {
        WebTypes.DescriptionMarkup.HTML -> { doc -> doc }
        WebTypes.DescriptionMarkup.MARKDOWN -> { doc -> HtmlMarkdownUtils.toHtml(doc, false) }
        else -> { doc -> "<p>" + StringUtil.escapeXmlEntities(doc).replace(EOL_PATTERN, "<br>") }
      }

    override val defaultIcon: Icon? = webTypes.defaultIcon?.let { IconLoader.createLazy { loadIcon(it) ?: EmptyIcon.ICON_0 } }

    override fun renderDescription(description: String): String? = descriptionRenderer(description)

    override fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement? =
      resolveSourceLocation(source)?.let { sourceSymbolResolver(it, cacheHolder) }

    override fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location? =
      symbolLocationResolver(source)

    override fun loadIcon(path: String): Icon? =
      if (path.startsWith("<svg"))
        WebTypesSvgStringIconLoader.loadIcon(path)
      else
        iconLoader(path)

    override fun matchContext(context: WebSymbolsContext): Boolean =
      (framework == null || context.framework == framework)
      && contextExpr?.evaluate(context) != false

    override fun toString(): String {
      return "$library@$version ($framework)"
    }

  }

  private class WebTypesSymbolNameConversionRulesProvider(private val framework: FrameworkId,
                                                          private val scope: WebTypesSymbolsScopeBase) : WebSymbolNameConversionRulesProvider {
    override fun getNameConversionRules(): WebSymbolNameConversionRules =
      scope.nameConversionRulesCache.value[framework] ?: WebSymbolNameConversionRules.empty()

    override fun createPointer(): Pointer<out WebSymbolNameConversionRulesProvider> {
      val framework = framework
      val scopePtr = scope.createPointer()
      return Pointer {
        scopePtr.dereference()?.let { WebTypesSymbolNameConversionRulesProvider(framework, scope) }
      }
    }

    override fun getModificationCount(): Long =
      scope.modificationCount
  }

  companion object {
    private val EOL_PATTERN: Regex = Regex("\n|\r\n")

  }

}
