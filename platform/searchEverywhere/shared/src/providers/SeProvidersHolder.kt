// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder.Companion.initialize
import fleet.kernel.DurableRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * We could have just one Map<SeProviderId, SeItemDataProvider>, but we can't, because in the previous Search Everywhere implementation
 * separate tab providers may differ from the all tab providers.
 * @see [initialize]
 */
@ApiStatus.Internal
class SeProvidersHolder(
  private val allTabProviders: Map<SeProviderId, SeLocalItemDataProvider>,
  private val separateTabProviders: Map<SeProviderId, SeLocalItemDataProvider>,
  private val legacyAllTabContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  private val legacySeparateTabContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
) : Disposable {
  fun get(providerId: SeProviderId, isAllTab: Boolean): SeLocalItemDataProvider? =
    if (isAllTab) allTabProviders[providerId]
    else separateTabProviders[providerId] ?: allTabProviders[providerId]

  override fun dispose() {
    allTabProviders.values.forEach { Disposer.dispose(it) }
    separateTabProviders.values.forEach { Disposer.dispose(it) }
  }

  fun getLegacyContributor(providerId: SeProviderId, isAllTab: Boolean): SearchEverywhereContributor<Any>? {
    return when {
      isAllTab -> legacyAllTabContributors[providerId]
      else -> legacySeparateTabContributors[providerId] ?: legacyAllTabContributors[providerId]
    }
  }

  fun getEssentialAllTabProviderIds(): Set<SeProviderId> =
    legacyAllTabContributors.filter {
      allTabProviders.contains(it.key) && EssentialContributor.checkEssential(it.value)
    }.keys

  companion object {
    suspend fun initialize(
      initEvent: AnActionEvent,
      project: Project?,
      sessionRef: DurableRef<SeSessionEntity>,
      logLabel: String,
      providerIds: List<SeProviderId>? = null,
    ): SeProvidersHolder {
      val legacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
      val separateTabLegacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()

      initializeLegacyContributors(initEvent, project, legacyContributors, separateTabLegacyContributors)

      val dataContext = initEvent.dataContext

      val providers = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()
      val separateTabProviders = mutableMapOf<SeProviderId, SeLocalItemDataProvider>()

      SeItemsProviderFactory.EP_NAME.extensionList.filter {
        providerIds == null || SeProviderId(it.id) in providerIds
      }.forEach { providerFactory ->
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
          providers[SeProviderId(it.id)] = SeLocalItemDataProvider(it, sessionRef, logLabel)
        }

        separateTabProvider?.let {
          separateTabProviders[SeProviderId(it.id)] = SeLocalItemDataProvider(it, sessionRef, logLabel)
        }
      }

      legacyContributors.disposeAndFilterOutUnnecessaryLegacyContributors(providers.keys)
      separateTabLegacyContributors.disposeAndFilterOutUnnecessaryLegacyContributors(separateTabProviders.keys)

      return SeProvidersHolder(providers,
                               separateTabProviders,
                               legacyContributors,
                               separateTabLegacyContributors)
    }

    private fun MutableMap<SeProviderId, SearchEverywhereContributor<Any>>.disposeAndFilterOutUnnecessaryLegacyContributors(providerIds: Set<SeProviderId>) {
      val contributorsToDispose = filter { !providerIds.contains(it.key) }

      contributorsToDispose.forEach {
        Disposer.dispose(it.value)
        remove(it.key)
      }
    }

    private suspend fun initializeLegacyContributors(
      initEvent: AnActionEvent,
      project: Project?,
      allContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
      separateTabContributors: MutableMap<SeProviderId, SearchEverywhereContributor<Any>>,
    ) {
      withContext(Dispatchers.EDT) {
        SearchEverywhereManagerImpl.createContributors(initEvent, project)
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
      }.getOrLogException { t ->
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
