// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.TabsCustomizationStrategy
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder.Companion.initialize
import fleet.kernel.DurableRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * We could have just one Map<SeProviderId, SeItemDataProvider>, but we can't, because in the previous Search Everywhere implementation
 * separate tab providers may differ from the all tab providers.
 * @see [initialize]
 */
@ApiStatus.Internal
class SeProvidersHolder(
  private val allTabProviders: Map<SeProviderId, SeItemDataProvider>,
  private val separateTabProviders: Map<SeProviderId, SeItemDataProvider>,
) : Disposable {
  fun get(providerId: SeProviderId, isSeparateTab: Boolean): SeItemDataProvider? =
    if (isSeparateTab) separateTabProviders[providerId] ?: allTabProviders[providerId]
    else allTabProviders[providerId]

  override fun dispose() {
    allTabProviders.values.forEach { Disposer.dispose(it) }
    separateTabProviders.values.forEach { Disposer.dispose(it) }
  }

  companion object {
    suspend fun initialize(
      initEvent: AnActionEvent,
      project: Project?,
      sessionRef: DurableRef<SeSessionEntity>,
      logLabel: String,
      providerIds: List<SeProviderId>? = null,
    ): SeProvidersHolder {
      val allContributors = mutableMapOf<String, SearchEverywhereContributor<Any>>()
      val separateTabContributors = mutableMapOf<String, SearchEverywhereContributor<Any>>()

      initializeLegacyContributors(initEvent, project, allContributors, separateTabContributors)

      val dataContext = initEvent.dataContext

      val providers = mutableMapOf<SeProviderId, SeItemDataProvider>()
      val separateTabProviders = mutableMapOf<SeProviderId, SeItemDataProvider>()

      SeItemsProviderFactory.EP_NAME.extensionList.filter {
        providerIds == null || SeProviderId(it.id) in providerIds
      }.forEach { providerFactory ->
        val provider: SeItemsProvider?
        val separateTabProvider: SeItemsProvider?

        if (providerFactory is SeWrappedLegacyContributorItemsProviderFactory) {
          provider = allContributors[providerFactory.id]?.let {
            providerFactory.getItemsProviderCatchingOrNull(project, it)
          }
          separateTabProvider = separateTabContributors[providerFactory.id]?.let {
            providerFactory.getItemsProviderCatchingOrNull(project,it)
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

      return SeProvidersHolder(providers, separateTabProviders)
    }

    private suspend fun initializeLegacyContributors(
      initEvent: AnActionEvent,
      project: Project?,
      allContributors: MutableMap<String, SearchEverywhereContributor<Any>>,
      separateTabContributors: MutableMap<String, SearchEverywhereContributor<Any>>,
    ) {
      withContext(Dispatchers.EDT) {
        SearchEverywhereManagerImpl.createContributors(initEvent, project)
      }.filterIsInstance<SearchEverywhereContributor<Any>>().forEach {
        allContributors[it.searchProviderId] = it
      }

      // From com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader.createTabs
      try {
        withContext(Dispatchers.EDT) {
          TabsCustomizationStrategy.getInstance().getSeparateTabContributors(allContributors.values.toList())
            .filterIsInstance<SearchEverywhereContributor<Any>>()
            .associateBy { it.searchProviderId }
        }
      }
      catch (e: Exception) {
        Logger.getInstance(SearchEverywhereHeader::class.java).error(e)
        allContributors.filter { it.value.isShownInSeparateTab }
      }.forEach {
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
  try {
    block()
  }
  catch (c: CancellationException) {
    throw c
  }
  catch (e: Exception) {
    SeLog.warn("SearchEverywhere items provider wasn't created: ${id}. Exception:\n${e.message}")
    null
  }