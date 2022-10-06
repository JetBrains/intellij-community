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
import com.intellij.webSymbols.registry.impl.SearchMap
import com.intellij.webSymbols.registry.*
import com.intellij.webSymbols.utils.HtmlMarkdownUtils
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper.Companion.wrap
import com.intellij.webSymbols.webTypes.impl.wrap
import com.intellij.webSymbols.webTypes.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.Icon

abstract class WebTypesSymbolsContainerBase : WebSymbolsContainer, WebSymbolsContextRulesProvider, WebSymbolNameConversionRules {

  private val namesProviderCache: MutableMap<WebSymbolNamesProvider, NameProvidersCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var namesProviderCacheMisses = 0

  private val registryCache: MutableMap<WebSymbolsRegistry, RegistryContributionsCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var registryCacheMisses = 0

  private val roots = mutableMapOf<Contributions, WebTypesJsonOrigin>()

  private val frameworkConfigs = mutableMapOf<WebTypes, FrameworkConfig>()
  private val contextsConfigs = mutableMapOf<WebTypes, ContextsConfig>()

  private val contextRulesCache = createContextRulesCache()

  private val canonicalNamesProvidersCache = createNameProvidersCache(
    { it.canonicalNames?.additionalProperties },
    { mergeConverters(listOf(it)) })
  private val matchNamesProvidersCache = createNameProvidersCache(
    { it.matchNames?.additionalProperties },
    { mergeConverters(it) })
  private val nameVariantsProvidersCache = createNameProvidersCache(
    { it.nameVariants?.additionalProperties },
    { mergeConverters(it) })

  @JvmField
  protected var modCount: Long = 0

  abstract override fun createPointer(): Pointer<out WebTypesSymbolsContainerBase>

  override val canonicalNamesProviders: Map<Triple<FrameworkId?, SymbolNamespace, SymbolKind>, Function<String, List<String>>>
    get() = canonicalNamesProvidersCache.value
  override val matchNamesProviders: Map<Triple<FrameworkId?, SymbolNamespace, SymbolKind>, Function<String, List<String>>>
    get() = matchNamesProvidersCache.value
  override val nameVariantsProviders: Map<Triple<FrameworkId?, SymbolNamespace, SymbolKind>, Function<String, List<String>>>
    get() = nameVariantsProvidersCache.value

  override fun getModificationCount(): Long =
    modCount

  final override fun getSymbols(namespace: SymbolNamespace?,
                                kind: String,
                                name: String?,
                                params: WebSymbolsNameMatchQueryParams,
                                context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    if (namespace != null) {
      getMaps(params).flatMap {
        it.getSymbols(namespace, kind, name, params, Stack(context))
      }.toList()
    }
    else emptyList()


  final override fun getCodeCompletions(namespace: SymbolNamespace?,
                                        kind: String,
                                        name: String?,
                                        params: WebSymbolsCodeCompletionQueryParams,
                                        context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    if (namespace != null) {
      getMaps(params).flatMap {
        it.getCodeCompletions(namespace, kind, name, params, Stack(context))
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
                          context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    getMap(params.registry, host, origin)
      .getSymbols(namespace ?: defaultNamespace, kind, name, params, context)
      .toList()

  internal fun getCodeCompletions(host: GenericContributionsHost,
                                  defaultNamespace: SymbolNamespace,
                                  origin: WebTypesJsonOrigin,
                                  namespace: SymbolNamespace?,
                                  kind: String,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    getMap(params.registry, host, origin)
      .getCodeCompletions(namespace ?: defaultNamespace, kind, name, params, context)
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
    registryCache.clear()
    contextRulesCache.drop()
    canonicalNamesProvidersCache.drop()
    matchNamesProvidersCache.drop()
    nameVariantsProvidersCache.drop()
  }

  private fun getMaps(params: WebSymbolsRegistryQueryParams): Sequence<ContributionSearchMap> =
    roots.asSequence()
      .filter { (_, origin) -> origin.matchContext(params.registry.context) }
      .map { (contributions, origin) ->
        getMap(params.registry, contributions, origin)
      }

  private fun getMap(registry: WebSymbolsRegistry,
                     host: GenericContributionsHost,
                     origin: WebTypesJsonOrigin): ContributionSearchMap =
    getOrCreateMap(registry, host) { consumer ->
      host.getAllContributions(origin.framework)
        .forEach { (namespace, kind, list) ->
          list.forEach { consumer(it.wrap(origin, this@WebTypesSymbolsContainerBase, namespace, kind)) }
        }
    }


  private fun getMap(registry: WebSymbolsRegistry,
                     root: Contributions,
                     origin: WebTypesJsonOrigin): ContributionSearchMap =
    getOrCreateMap(registry, root) { consumer ->
      root.getAllContributions(origin.framework)
        .forEach { (namespace, kind, list) ->
          list.asSequence()
            .map { it.wrap(origin, this@WebTypesSymbolsContainerBase, namespace, kind) }
            .forEach(consumer)
        }
    }

  private fun getOrCreateMap(registry: WebSymbolsRegistry,
                             key: Any,
                             mapInitializer: (consumer: (WebTypesJsonContributionWrapper) -> Unit) -> Unit): ContributionSearchMap =
    getNameProvidersCache(registry.namesProvider).getOrCreateMap(key, mapInitializer)

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

  private fun getRegistryContributionsCache(registry: WebSymbolsRegistry): RegistryContributionsCache {
    if (registryCacheMisses > 100) {
      // Get rid of old soft keys
      registryCacheMisses = 0
      registryCache.clear()
    }
    return registryCache.computeIfAbsent(registry) { registryCacheMisses++; RegistryContributionsCache(registry) }
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
        .flatMap {it.additionalProperties.entries}
        .filter { (name,config) -> name != null && config.kind != null }
        .map { (name, config) -> RulesEntry(config.kind, name, config.enableWhen?.wrap(), config.disableWhen?.wrap()) }
        .plus(
          frameworkConfigs
            .filter { (webTypes, _) -> webTypes.framework != null }
            .map {(webTypes, config) ->
              RulesEntry(KIND_FRAMEWORK, webTypes.framework, config.enableWhen?.wrap(), config.disableWhen?.wrap())
          }
        )
        .groupBy { it.kind }

      val result = MultiMap.create<ContextKind, WebSymbolsContextKindRules>()
      rulesPerKind.forEach { (kind, rules) ->
        val rulesPerName = rules.groupBy { it.name }
        val enablementRules = rulesPerName.mapValues { (_,entries) -> entries.mapNotNull { it.enablementRules } }
        val disablementRules = rulesPerName.mapValues { (_,entries) -> entries.mapNotNull { it.disablementRules } }
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

  private fun <T, K> createNameProvidersCache(accessor: (config: FrameworkConfig) -> Map<String, T>?, mapper: (T) -> K):
    ClearableLazyValue<Map<Triple<FrameworkId?, SymbolNamespace, SymbolKind>, K>> =
    ClearableLazyValue.create {
      frameworkConfigs.asSequence()
        .flatMap { config ->
          val framework = config.key.framework ?: return@flatMap emptySequence()
          val map = accessor(config.value) ?: return@flatMap emptySequence()
          mapNameConverters(map, mapper, framework)
        }
        .distinctBy { it.first }
        .toMap()
    }

  private inner class ContributionSearchMap(namesProvider: WebSymbolNamesProvider)
    : SearchMap<WebTypesJsonContributionWrapper>(namesProvider) {

    fun add(item: WebTypesJsonContributionWrapper) {
      add(item.namespace, item.kind, item.name, item.jsonPattern?.wrap(null), item)
    }

    override fun Sequence<WebTypesJsonContributionWrapper>.mapAndFilter(params: WebSymbolsRegistryQueryParams): Sequence<WebSymbol> {
      val cache = getRegistryContributionsCache(params.registry)
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

  private inner class RegistryContributionsCache(private val registry: WebSymbolsRegistry) {
    private val symbolsCache: MutableMap<WebTypesJsonContributionWrapper, WebSymbol> = ConcurrentHashMap()
    private var registryTimestamp: Long = -1

    fun getOrCreateSymbol(item: WebTypesJsonContributionWrapper): WebSymbol =
      symbolsCache.getOrPut(item) { item.withRegistryContext(registry) }

    fun checkForModifications() {
      if (registry.modificationCount != this.registryTimestamp) {
        synchronized(this) {
          if (registry.modificationCount != this.registryTimestamp) {
            symbolsCache.clear()
            this.registryTimestamp = registry.modificationCount
          }
        }
      }
    }

  }

  class WebTypesJsonOriginImpl(
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

  companion object {
    private val EOL_PATTERN: Regex = Regex("\n|\r\n")

  }

  @Suppress("DEPRECATION")
  interface WebTypesJsonOrigin : WebSymbolOrigin {
    @JvmDefault
    override val typeSupport: WebTypesSymbolTypeSupport?
      get() = null

    fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement?
    fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location?
    fun renderDescription(description: String): String?
    fun loadIcon(path: String): Icon?
    fun matchContext(context: WebSymbolsContext): Boolean
  }

}
