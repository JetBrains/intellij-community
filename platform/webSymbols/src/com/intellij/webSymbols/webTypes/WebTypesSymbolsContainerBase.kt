// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EmptyIcon
import com.intellij.webSymbols.*
import com.intellij.webSymbols.framework.WebFrameworksConfiguration
import com.intellij.webSymbols.impl.SearchMap
import com.intellij.webSymbols.utils.HtmlMarkdownUtils
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper
import com.intellij.webSymbols.webTypes.impl.WebTypesJsonContributionWrapper.Companion.wrap
import com.intellij.webSymbols.webTypes.impl.wrap
import com.intellij.webSymbols.webTypes.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.Icon

abstract class WebTypesSymbolsContainerBase : WebSymbolsContainer, WebFrameworksConfiguration {

  private val namesProviderCache: MutableMap<WebSymbolNamesProvider, NameProvidersCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var namesProviderCacheMisses = 0

  private val registryCache: MutableMap<WebSymbolsRegistry, RegistryContributionsCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var registryCacheMisses = 0

  private val roots = mutableMapOf<Contributions, WebTypesJsonOrigin>()

  private val configs = mutableMapOf<WebTypes, FrameworkConfig>()

  private val enableWhenCache = createEnablementCache { it.enableWhen?.wrap() }
  private val disableWhenCache = createEnablementCache { it.disableWhen?.wrap() }
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

  override val enableWhen: Map<String, List<WebFrameworksConfiguration.EnablementRules>>
    get() = enableWhenCache.value
  override val disableWhen: Map<String, List<WebFrameworksConfiguration.DisablementRules>>
    get() = disableWhenCache.value
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
    if (framework != null) {
      webTypes.frameworkConfig?.let {
        configs[webTypes] = it
        dropCaches()
      }
    }
  }

  protected fun removeWebTypes(webTypes: WebTypes) {
    modCount++

    removeRoot(webTypes.contributions)

    if (configs.remove(webTypes) != null) {
      dropCaches()
    }
  }

  private fun dropCaches() {
    namesProviderCache.clear()
    registryCache.clear()
    enableWhenCache.drop()
    disableWhenCache.drop()
    canonicalNamesProvidersCache.drop()
    matchNamesProvidersCache.drop()
    nameVariantsProvidersCache.drop()
  }

  private fun getMaps(params: WebSymbolsRegistryQueryParams): Sequence<ContributionSearchMap> =
    roots.asSequence()
      .filter { (_, origin) -> origin.framework == params.framework || origin.framework == null }
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

  private fun <T> createEnablementCache(accessor: (config: FrameworkConfig) -> T?): ClearableLazyValue<Map<String, List<T>>> =
    ClearableLazyValue.create {
      configs.asSequence()
        .mapNotNull {
          val framework = it.key.framework ?: return@mapNotNull null
          val rules = accessor(it.value) ?: return@mapNotNull null
          Pair(framework, rules)
        }
        .groupBy({ it.first }, { it.second })
    }

  private fun <T, K> createNameProvidersCache(accessor: (config: FrameworkConfig) -> Map<String, T>?, mapper: (T) -> K):
    ClearableLazyValue<Map<Triple<FrameworkId?, SymbolNamespace, SymbolKind>, K>> =
    ClearableLazyValue.create {
      configs.asSequence()
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
    private val typeResolver: WebTypesSymbolTypeResolver,
    private val symbolLocationResolver: (source: SourceBase) -> WebTypesSymbol.Location? = { null },
    private val sourceSymbolResolver: (location: WebTypesSymbol.Location, cacheHolder: UserDataHolderEx) -> PsiElement? = { _, _ -> null },
    private val iconLoader: (path: String) -> Icon? = { null },
    override val version: String? = webTypes.version
  ) : WebTypesJsonOrigin {

    override val framework: FrameworkId? = webTypes.framework
    override val library: String? = webTypes.name

    private val descriptionRenderer: (String) -> String? =
      when (webTypes.descriptionMarkupWithLegacy) {
        WebTypes.DescriptionMarkup.HTML -> { doc -> doc }
        WebTypes.DescriptionMarkup.MARKDOWN -> { doc -> HtmlMarkdownUtils.toHtml(doc, false) }
        else -> { doc -> "<p>" + StringUtil.escapeXmlEntities(doc).replace(EOL_PATTERN, "<br>") }
      }

    override val defaultIcon: Icon? = webTypes.defaultIcon?.let { IconLoader.createLazy { loadIcon(it) ?: EmptyIcon.ICON_0 } }

    override fun renderDescription(description: String): String? = descriptionRenderer(description)

    override fun getType(typeReferences: List<Type>): Any? =
      typeResolver.resolveType(typeReferences.mapNotNull {
        when (val reference = it.value) {
          is String -> WebTypesSymbolTypeResolver.TypeReference(null, reference)
          is TypeReference -> if (reference.name != null)
            WebTypesSymbolTypeResolver.TypeReference(reference.module, reference.name)
          else null
          else -> null
        }
      })

    override fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement? =
      resolveSourceLocation(source)?.let { sourceSymbolResolver(it, cacheHolder) }

    override fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location? =
      symbolLocationResolver(source)

    override fun loadIcon(path: String): Icon? =
      if (path.startsWith("<svg"))
        WebTypesSvgStringIconLoader.loadIcon(path)
      else
        iconLoader(path)

    override fun toString(): String {
      return "$library@$version ($framework)"
    }
  }

  companion object {
    private val EOL_PATTERN: Regex = Regex("\n|\r\n")

  }

  interface WebTypesJsonOrigin : WebSymbolsContainer.Origin {
    fun getType(typeReferences: List<Type>): Any?
    fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement?
    fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location?
    fun renderDescription(description: String): String?
    fun loadIcon(path: String): Icon?
  }

}
