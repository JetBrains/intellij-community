// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.EssentialContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.TabsCustomizationStrategy
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
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder.Companion.initialize
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
  val legacyContributors: SeLegacyContributors
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
      val legacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
      val separateTabLegacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()

      initializeLegacyContributors(initEvent, project, legacyContributors, separateTabLegacyContributors)

      val dataContext = initEvent.dataContext

      val allTabProviders = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()
      val separateTabProviders = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()

      SeItemsProviderFactory.EP_NAME.extensionList.forEach { providerFactory ->
        val provider: SeItemsProvider?
        val separateTabProvider: SeItemsProvider?

        val providerFactoryId = providerFactory.id.let {
          SeProviderId(if (it.startsWith(SeProviderIdUtils.TOP_HIT_ID)) SeProviderIdUtils.TOP_HIT_ID else it)
        }

        if (providerFactory is SeWrappedLegacyContributorItemsProviderFactory) {
          provider = legacyContributors[providerFactoryId]?.let {
            providerFactory.getItemsProviderCatchingOrNull(project, it)
          }
          separateTabProvider = separateTabLegacyContributors[providerFactoryId]?.let {
            providerFactory.getItemsProviderCatchingOrNull(project, it)
          }
        }
        else {
          provider = providerFactory.getItemsProviderCatchingOrNull(project, dataContext)
          separateTabProvider = null

          if (provider?.id != providerFactory.id) {
            SeLog.log { "Provider ID ($logLabel) doesn't match their factory ID: ${provider?.id} != ${providerFactory.id}" }
          }
        }

        provider?.let {
          allTabProviders[SeProviderId(it.id)] = SeLocalItemDataProvider(it, session, logLabel)
        }

        separateTabProvider?.let {
          separateTabProviders[SeProviderId(it.id)] = SeLocalItemDataProvider(it, session, logLabel)
        }
      }

      if (withAdaptedLegacyContributors) {
        val presentationProviders = SeLegacyItemPresentationProvider.EP_NAME.extensionList.associateBy { it.id.toProviderId() }
        allTabProviders.putAll(createAdaptedProvidersIfNecessary(legacyContributors, allTabProviders, presentationProviders, session, logLabel))
        separateTabProviders.putAll(createAdaptedProvidersIfNecessary(separateTabLegacyContributors, separateTabProviders, presentationProviders, session, logLabel))
      }

      return SeProvidersHolder(allTabProviders,
                               separateTabProviders,
                               SeLegacyContributors(legacyContributors, separateTabLegacyContributors))
    }

    private fun createAdaptedProvidersIfNecessary(legacyContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
                                                  supportedProviders: Map<SeProviderId, SeLocalItemDataProvider>,
                                                  presentationProviders: Map<SeProviderId, SeLegacyItemPresentationProvider>,
                                                  session: SeSession,
                                                  logLabel: String): List<Pair<SeProviderId, SeLocalItemDataProvider>> {
      val providerIds = supportedProviders.keys.map {
        if (it.value.startsWith(SeProviderIdUtils.TOP_HIT_ID)) SeProviderId(SeProviderIdUtils.TOP_HIT_ID) else it
      }

      return legacyContributors.filter { !providerIds.contains(it.key) }.map {
        it.key to SeLocalItemDataProvider(SeAdaptedItemsProvider(it.value, presentationProviders[it.key]), session, logLabel)
      }
    }

    private suspend fun initializeLegacyContributors(
      initEvent: AnActionEvent,
      project: Project?,
      allContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
      separateTabContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
    ) {
      withContext(Dispatchers.EDT) {
        SearchEverywhereManagerImpl.createContributors(initEvent, project, false)
      }.filterIsInstance<SearchEverywhereContributor<Any>>().forEach {
        allContributors[SeProviderId(it.searchProviderId)] = it
      }

      // From com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader.createTabs
      (runCatching {
        withContext(Dispatchers.EDT) {
          TabsCustomizationStrategy.getInstance().getSeparateTabContributors(allContributors.values.toList())
            .filterIsInstance<SearchEverywhereContributor<Any>>()
            .associateBy { SeProviderId(it.searchProviderId) }
        }
      }.getOrHandleException { t ->
        Logger.getInstance(SearchEverywhereHeader::class.java).error(t)
      } ?: allContributors.filter { it.value.isShownInSeparateTab }).forEach {
        separateTabContributors[it.key] = it.value
      }
    }
  }
}

@ApiStatus.Internal
suspend fun SeItemsProviderFactory.getItemsProviderCatchingOrNull(project: Project?, dataContext: DataContext): SeItemsProvider? =
  computeCatchingOrNull { getItemsProvider(project, dataContext) }

@ApiStatus.Internal
suspend fun SeWrappedLegacyContributorItemsProviderFactory.getItemsProviderCatchingOrNull(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? =
  computeCatchingOrNull { getItemsProvider(project, legacyContributor) }

@ApiStatus.Internal
suspend fun SeItemsProviderFactory.computeCatchingOrNull(block: suspend () -> SeItemsProvider?): SeItemsProvider? =
  computeCatchingOrNull({ e -> "SearchEverywhere items provider wasn't created: ${id}. Exception:\n${e.message}" }, block)

@ApiStatus.Internal
@Serializable
class SeLegacyTabInfo(val providerId: SeProviderId, val tabSortWeight: Int, val tabName: @Nls String)