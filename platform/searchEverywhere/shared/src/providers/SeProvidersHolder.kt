// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.EssentialContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacement
import com.intellij.ide.actions.searcheverywhere.TabsCustomizationStrategy
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeLegacyItemPresentationProvider
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.toProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * We could have just one Map<SeProviderId, SeItemDataProvider>, but we can't, because in the previous Search Everywhere implementation
 * separate tab providers may differ from the all tab providers.
 * @see [initialize]
 */
@ApiStatus.Internal
class SeProvidersHolder(
  private val allTabProviders: Map<SeProviderId, SeLocalItemDataProvider>,
  private val separateTabProviders: Map<SeProviderId, SeLocalItemDataProvider>,
  val legacyContributors: SeLegacyContributors,
  private val searchProjectHolders: SeSearchProjectHolders? = null,
) : Disposable {
  fun adaptedAllTabProviders(withPresentation: Boolean): Set<SeProviderId> =
    allTabProviders.values.mapNotNull { it.takeIf { it.isAdapted && (it.isAdaptedWithPresentation == withPresentation) }?.id }.toSet()

  fun adaptedTabInfos(withPresentation: Boolean): List<SeLegacyTabInfo> =
    separateTabProviders.values.mapNotNull {
      if (!it.isAdapted || (it.isAdaptedWithPresentation != withPresentation)) return@mapNotNull null
      val legacySeparateTabContributor = legacyContributors.separateTab[it.id] ?: return@mapNotNull null
      SeLegacyTabInfo(it.id, legacySeparateTabContributor.sortWeight, legacySeparateTabContributor.groupName)
    }

  fun get(providerId: SeProviderId, isAllTab: Boolean): SeLocalItemDataProvider? =
    if (isAllTab) allTabProviders[providerId]
    else separateTabProviders[providerId] ?: allTabProviders[providerId]

  override fun dispose() {
    allTabProviders.values.forEach { Disposer.dispose(it) }
    separateTabProviders.values.forEach { Disposer.dispose(it) }
    legacyContributors.allTab.values.forEach { Disposer.dispose(it) }
    legacyContributors.separateTab.values.forEach { Disposer.dispose(it) }
    searchProjectHolders?.let { Disposer.dispose(it) }
  }

  fun getLegacyContributor(providerId: SeProviderId, isAllTab: Boolean): SearchEverywhereContributor<Any>? {
    return when {
      isAllTab -> legacyContributors.allTab[providerId]
      else -> legacyContributors.separateTab[providerId] ?: legacyContributors.allTab[providerId]
    }
  }

  fun getEssentialAllTabProviderIds(): Set<SeProviderId> =
    legacyContributors.allTab.filter {
      (allTabProviders[it.key]?.isAdapted == false) && EssentialContributor.checkEssential(it.value)
    }.keys

  companion object {
    suspend fun initialize(
      initEvent: AnActionEvent,
      project: Project?,
      session: SeSession,
      logLabel: String,
      withAdaptedLegacyContributors: Boolean,
    ): SeProvidersHolder {
      // The providers of an id that the product names search every search project of the popup, see [SeSearchProjects].
      val searchProjects = project?.let { SeSearchProjectsProvider.find(it) }
      return initialize(initEvent, project, session, logLabel, withAdaptedLegacyContributors, searchProjects)
    }

    /**
     * The holder of one search [project] for [SeSearchProjectHolders]. The popup asks its providers directly.
     * So it asks no [SeSearchProjectsProvider], and it creates no contributor of another project and no holder of its own.
     */
    suspend fun initializeSearchProject(
      initEvent: AnActionEvent,
      project: Project,
      session: SeSession,
      logLabel: String,
    ): SeProvidersHolder =
      initialize(initEvent, project, session, logLabel, withAdaptedLegacyContributors = false, searchProjects = null)

    private suspend fun initialize(
      initEvent: AnActionEvent,
      project: Project?,
      session: SeSession,
      logLabel: String,
      withAdaptedLegacyContributors: Boolean,
      searchProjects: SeSearchProjects?,
    ): SeProvidersHolder {
      val legacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
      val separateTabLegacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()

      initializeLegacyContributors(initEvent, project, searchProjects, legacyContributors, separateTabLegacyContributors)

      val dataContext = initEvent.dataContext

      val allTabProviders = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()
      val separateTabProviders = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()
      val initializationGuard = SeProviderInitializationGuard.getInstance()
      val openingId = session.id

      val searchProjectHolders = searchProjects?.createHolders(initEvent, session)
      val searchedProviderIds = searchProjects?.providerIds.orEmpty()

      fun dataProvider(provider: SeItemsProvider, isAllTab: Boolean): SeLocalItemDataProvider {
        val holders = searchProjectHolders.takeIf { SeProviderId(provider.id) in searchedProviderIds }
        return SeLocalItemDataProvider(provider, session, logLabel, holders, isAllTab)
      }

      SeItemsProviderFactory.EP_NAME.extensionList.forEach { providerFactory ->
        val provider: SeItemsProvider?
        val separateTabProvider: SeItemsProvider?

        val providerFactoryId = SeProviderId(providerFactory.id)
        val pluginId = PluginManager.getPluginByClass(providerFactory.javaClass)?.pluginId?.idString

        if (providerFactory is SeWrappedLegacyContributorItemsProviderFactory) {
          provider = legacyContributors[providerFactoryId]?.let {
            initializationGuard.initialize(providerFactory.id, openingId, pluginId, logLabel) {
              providerFactory.getItemsProviderCatchingOrNull(project, it, isAllTab = true, dataContext = dataContext)
            }
          }
          separateTabProvider = separateTabLegacyContributors[providerFactoryId]?.let {
            initializationGuard.initialize(providerFactory.id, openingId, pluginId, logLabel) {
              providerFactory.getItemsProviderCatchingOrNull(project, it, isAllTab = false, dataContext = dataContext)
            }
          }
        }
        else if (providerFactory is SeTabAwareItemsProviderFactory) {
          provider = initializationGuard.initialize(providerFactory.id, openingId, pluginId, logLabel) {
            providerFactory.getItemsProviderCatchingOrNull(project, dataContext, isAllTab = true)
          }
          separateTabProvider = initializationGuard.initialize(providerFactory.id, openingId, pluginId, logLabel) {
            providerFactory.getItemsProviderCatchingOrNull(project, dataContext, isAllTab = false)
          }
        }
        else {
          provider = initializationGuard.initialize(providerFactory.id, openingId, pluginId, logLabel) {
            providerFactory.getItemsProviderCatchingOrNull(project, dataContext)
          }
          separateTabProvider = null

          if (provider?.id != providerFactory.id) {
            SeLog.log { "Provider ID ($logLabel) doesn't match their factory ID: ${provider?.id} != ${providerFactory.id}" }
          }
        }

        provider?.let {
          allTabProviders[SeProviderId(it.id)] = dataProvider(it, isAllTab = true)
        }

        separateTabProvider?.let {
          separateTabProviders[SeProviderId(it.id)] = dataProvider(it, isAllTab = false)
        }
      }

      if (withAdaptedLegacyContributors) {
        val presentationProviders = SeLegacyItemPresentationProvider.EP_NAME.extensionList.associateBy { it.id.toProviderId() }
        // A provider of the All tab serves the separate tab too, see [get].
        val providers = allTabProviders + separateTabProviders
        allTabProviders.putAll(createAdaptedProvidersIfNecessary(legacyContributors, allTabProviders, presentationProviders, session, logLabel))
        separateTabProviders.putAll(createAdaptedProvidersIfNecessary(separateTabLegacyContributors, providers, presentationProviders, session, logLabel))
      }

      return SeProvidersHolder(allTabProviders,
                               separateTabProviders,
                               SeLegacyContributors(legacyContributors, separateTabLegacyContributors),
                               searchProjectHolders)
    }

    private fun createAdaptedProvidersIfNecessary(legacyContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
                                                  supportedProviders: Map<SeProviderId, SeLocalItemDataProvider>,
                                                  presentationProviders: Map<SeProviderId, SeLegacyItemPresentationProvider>,
                                                  session: SeSession,
                                                  logLabel: String): List<Pair<SeProviderId, SeLocalItemDataProvider>> {
      return legacyContributors.filter { !supportedProviders.containsKey(it.key) }.map {
        it.key to SeLocalItemDataProvider(SeAdaptedItemsProvider(it.value, presentationProviders[it.key]), session, logLabel)
      }
    }

    /**
     * Creates the legacy contributors of [project] into [allContributors] and [separateTabContributors].
     *
     * Both steps run in one EDT block. A replacement of a separate tab contributor reads the event that
     * [SearchEverywhereManagerImpl.createContributors] saves in [SearchEverywhereMlContributorReplacement]. The holders
     * of several search projects initialize at the same time, and none can save its own event between the two steps.
     *
     * A contributor of an id of [searchProjects] that [project] lacks, but another search project has, is created too.
     * It finds nothing in [project], and the popup asks the other projects through it, see [SeLocalItemDataProvider].
     */
    private suspend fun initializeLegacyContributors(
      initEvent: AnActionEvent,
      project: Project?,
      searchProjects: SeSearchProjects?,
      allContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
      separateTabContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
    ) {
      withContext(Dispatchers.EDT) {
        SearchEverywhereManagerImpl.createContributors(initEvent, project, true, false)
          .filterIsInstance<SearchEverywhereContributor<Any>>()
          .forEach { allContributors[SeProviderId(it.searchProviderId)] = it }

        if (project != null && searchProjects != null) {
          addContributorsOfOtherProjects(initEvent, project, searchProjects, allContributors)
        }

        // From com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader.createTabs
        (runCatching {
          TabsCustomizationStrategy.getInstance().getSeparateTabContributors(allContributors.values.toList())
            .filterIsInstance<SearchEverywhereContributor<Any>>()
            .associateBy { SeProviderId(it.searchProviderId) }
        }.getOrHandleException { t ->
          Logger.getInstance(SearchEverywhereHeader::class.java).error(t)
        } ?: allContributors.filter { it.value.isShownInSeparateTab }).forEach {
          separateTabContributors[it.key] = it.value
        }
      }
    }
  }
}

/**
 * Adds to [contributors] the contributor of each id of [searchProjects] that [project] lacks and another search project
 * has. The factory of such a contributor is not available in [project], so the popup created none.
 */
private fun addContributorsOfOtherProjects(
  initEvent: AnActionEvent,
  project: Project,
  searchProjects: SeSearchProjects,
  contributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
) {
  val otherProjects = searchProjects.projects.filter { it !== project && !it.isDisposed }
  if (otherProjects.isEmpty() || searchProjects.providerIds.all { it in contributors }) return
  for (factory in SearchEverywhereContributor.EP_NAME.extensionList) {
    if (factory.isAvailable(project, true) || otherProjects.none { factory.isAvailable(it, true) }) continue
    @Suppress("UNCHECKED_CAST")
    val contributor = factory.createContributor(initEvent) as SearchEverywhereContributor<Any>
    val id = SeProviderId(contributor.searchProviderId)
    if (id in searchProjects.providerIds && id !in contributors) contributors[id] = contributor
    else Disposer.dispose(contributor)
  }
}

@ApiStatus.Internal
suspend fun SeItemsProviderFactory.getItemsProviderCatchingOrNull(project: Project?, dataContext: DataContext): SeItemsProvider? =
  computeCatchingOrNull { getItemsProvider(project, dataContext) }

@ApiStatus.Internal
suspend fun SeTabAwareItemsProviderFactory.getItemsProviderCatchingOrNull(project: Project?,
                                                                         dataContext: DataContext,
                                                                         isAllTab: Boolean): SeItemsProvider? =
  computeCatchingOrNull { getItemsProvider(project, dataContext, isAllTab) }

@ApiStatus.Internal
suspend fun SeWrappedLegacyContributorItemsProviderFactory.getItemsProviderCatchingOrNull(project: Project?,
                                                                                         legacyContributor: SearchEverywhereContributor<Any>,
                                                                                         isAllTab: Boolean,
                                                                                         dataContext: DataContext): SeItemsProvider? =
  computeCatchingOrNull { getItemsProvider(project, legacyContributor, isAllTab, dataContext) }

@ApiStatus.Internal
suspend fun SeItemsProviderFactory.computeCatchingOrNull(block: suspend () -> SeItemsProvider?): SeItemsProvider? =
  computeCatchingOrNull({ e -> "SearchEverywhere items provider wasn't created: ${id}. Exception:\n${e.stackTraceToString()}" }, block)

@ApiStatus.Internal
@Serializable
class SeLegacyTabInfo(val providerId: SeProviderId, val tabSortWeight: Int, val tabName: @Nls String)
