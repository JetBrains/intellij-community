// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeCommandsProviderInterface
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderWithPossibleOperationDisposable
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePossibleInternalCommandsHandling
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider
import com.intellij.platform.searchEverywhere.composedWeight
import com.intellij.platform.searchEverywhere.providers.commands.SeCommandItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilterProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

private fun SeItemsProvider.shouldTreatQueryAsCommand(query: String): Boolean {
  if (this !is SePossibleInternalCommandsHandling) return query.mightBeACommandQuery
  return shouldTreatAsACommandQuery(query) ?: query.mightBeACommandQuery
}

private fun SeItemsProvider.shouldTreatQueryAsCommandWithArg(query: String): Boolean {
  if (this !is SePossibleInternalCommandsHandling) return query.mightBeACommandWithArg
  return shouldTreatAsACommandQueryWithArg(query) ?: query.mightBeACommandWithArg
}

private val String.mightBeACommandQuery get() = startsWith(SearchTopHitProvider.getTopHitAccelerator()) && !contains(" ")
private val String.mightBeACommandWithArg get() = startsWith(SearchTopHitProvider.getTopHitAccelerator()) && contains(" ")

@ApiStatus.Internal
fun SeItemsProvider.isPreviewEnabled(): Boolean {
  return this is SeItemsPreviewProvider
}

@ApiStatus.Internal
fun SeItemsProvider.isExtendedInfoEnabled(): Boolean {
  return this is SeExtendedInfoProvider
}

@ApiStatus.Internal
suspend fun SeItemsProvider.getSearchScopesInfo(): SearchScopesInfo? =
  when (this) {
    is SeSearchScopesProvider -> getSearchScopesInfo()
    is SeTargetsFilterProvider -> targetItemsProvider.getSearchScopesInfo()
    else -> null
  }

@ApiStatus.Internal
suspend fun SeItemsProvider.getTypeVisibilityStates(key: String): List<SeTypeVisibilityStatePresentation>? =
  when (this) {
    is SeTypeVisibilityStateProvider -> getTypeVisibilityStates(key)
    is SeTargetsFilterProvider -> targetItemsProvider.getTypeVisibilityStates(key)
    else -> null
  }

@ApiStatus.Internal
fun SeItemsProvider.areCommandsSupported(): Boolean {
  if (this is SeAdaptedItemsProvider) {
    return this.isCommandsSupported()
  }
  return this is SeCommandsProviderInterface
}

@ApiStatus.Internal
fun SeItemsProvider.getSupportedCommands(): List<SeCommandInfo> {
  if (this is SeAdaptedItemsProvider) {
    return this.getSupportedCommands()
  }
  return (this as? SeCommandsProviderInterface)?.getSupportedCommands() ?: emptyList()
}

@ApiStatus.Internal
class SeLocalItemDataProvider(
  val provider: SeItemsProvider,
  private val session: SeSession,
  private val logLabel: String = "Local",
  /**
   * The providers of the same id in the other search projects of the popup, or `null` when the popup searches one project.
   * [provider] stays the provider of the project of the popup, and it answers every question about the provider itself.
   */
  private val searchProjectProviders: SeSearchProjectProviders.Source? = null,
) : Disposable {
  val id: SeProviderId
    get() = SeProviderId(provider.id)
  val displayName: @Nls String
    get() = provider.displayName
  val isAdapted: Boolean
    get() = provider is SeAdaptedItemsProvider
  val isAdaptedWithPresentation: Boolean
    get() = provider is SeAdaptedItemsProvider && provider.hasPresentationProvider

  private val infoWithReportableId = mapOf(
    SeItemDataKeys.REPORTABLE_PROVIDER_ID to
      if (SearchEverywhereUsageTriggerCollector.isReportable(provider)) SeProviderIdUtils.reportableId(provider.id)
      else SearchEverywhereUsageTriggerCollector.NOT_REPORTABLE_ID
  )

  @OptIn(ExperimentalAtomicApi::class)
  fun getItems(params: SeParams): Flow<SeItemData> {
    val counter = AtomicInt(0)

    val commandItemsFlow = createCommandItemsFlow(params, counter)
    val rawItemsFlow = createRawItemsFlow(params, counter)

    return merge(commandItemsFlow, rawItemsFlow)
      .buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  @OptIn(ExperimentalAtomicApi::class)
  private fun createCommandItemsFlow(
    params: SeParams,
    counter: AtomicInt,
  ): Flow<SeItemData> = channelFlow {
    val supportedCommands = provider.getSupportedCommands()
    val commandItems = getCommandItems(params, supportedCommands)

    for (item in commandItems) {
      val itemData =
        SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), item, id, mapOf(SeItemDataKeys.IS_COMMAND to "true"))
        ?: continue

      val count = counter.incrementAndFetch()
      SeLog.log(SeLog.ITEM_EMIT) {
        "$logLabel provider for ${id.value} receives command (total=$count): " +
        "${itemData.presentation.text.split("\n").firstOrNull()}"
      }

      send(itemData)
    }
  }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)

  private fun getCommandItems(params: SeParams, supportedCommands: List<SeCommandInfo>): List<SeCommandItem> {
    val inputQuery = params.inputQuery
    if (!provider.shouldTreatQueryAsCommand(inputQuery)) return emptyList()

    val commandPrefix = SearchTopHitProvider.getTopHitAccelerator()
    val typedCommand = inputQuery.removePrefix(commandPrefix)
    val matchingCommands = supportedCommands
      .filter { it.command.contains(typedCommand) }

    SeLog.log(SeLog.ITEM_EMIT) {
      "Command item list completed - $logLabel - ${matchingCommands.size}"
    }

    return matchingCommands.map { SeCommandItem(it) }
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun createRawItemsFlow(
    params: SeParams,
    counter: AtomicInt,
  ): Flow<SeItemData> = getOwnedRawItems(params).mapNotNull { (item, searchProjectKey) ->
    // A provider can find a command item. The selection of such an item sets the search text to the command.
    var additionalInfo = if (item is SeCommandItem) infoWithReportableId + (SeItemDataKeys.IS_COMMAND to "true") else infoWithReportableId
    if (searchProjectKey != null) additionalInfo = additionalInfo + (SeItemDataKeys.SEARCH_PROJECT to searchProjectKey)
    val itemData = SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), item, id, additionalInfo)
    itemData?.also {
      SeLog.log(SeLog.ITEM_EMIT) {
        val count = counter.incrementAndFetch()
        "$logLabel provider for ${id.value} receives (total=$count, priority=${itemData.composedWeight}): ${itemData.uuid} - ${itemData.presentation.text.split("\n").firstOrNull()}"
      }
    }
  }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)

  /**
   * The items of every provider that [params] asks, each with the key of its search project.
   * The key is `null` when the popup searches one project.
   */
  private fun itemsRawChannelFlow(params: SeParams, operationDisposable: Disposable?): Flow<OwnedItem> {
    return channelFlow {
      for ((provider, searchProjectKey) in searchedProviders(params)) {
        launch {
          try {
            collectItemsWithRetry(provider, params, operationDisposable) { item ->
              send(OwnedItem(item, searchProjectKey))
              coroutineContext.isActive
            }
          }
          catch (e: Throwable) {
            rethrowControlFlowException(e)
            SeLog.warn("Error while collecting items from ${provider.id}($logLabel) provider: ${e.message}")
          }
        }
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).onCompletion {
      SeLog.log(SeLog.ITEM_EMIT) { "Item data provider flow completed - $logLabel - ${id.value}" }
    }
  }

  /**
   * The providers that [params] asks: [provider] alone, or the providers of the search projects that the filter picks,
   * see [SeSearchProjectsFilter]. A pick that names no search project of the popup asks every search project: the
   * picked project closed after the filter took it.
   */
  private suspend fun searchedProviders(params: SeParams): List<Pair<SeItemsProvider, String?>> {
    val source = searchProjectProviders ?: return listOf(provider to null)
    val all = listOfNotNull(source.popupProjectKey?.let { provider to it }) + source.providers().map { it.provider to it.key }
    val selectedKeys = SeSearchProjectsFilter.selectedKeys(params.filter) ?: return all
    return all.filter { (_, key) -> key in selectedKeys }.ifEmpty { all }
  }

  /** The provider that gave [itemData]: the provider of its search project, else [provider]. */
  fun providerOf(itemData: SeItemData): SeItemsProvider = searchProjectProviderOf(itemData)?.provider ?: provider

  private fun searchProjectProviderOf(itemData: SeItemData): SeSearchProjectItemsProvider? {
    val key = itemData.additionalInfo[SeItemDataKeys.SEARCH_PROJECT] ?: return null
    return searchProjectProviders?.createdProviders()?.firstOrNull { it.key == key }
  }

  private suspend fun collectItemsWithRetry(
    provider: SeItemsProvider,
    params: SeParams,
    operationDisposable: Disposable?,
    collector: SeItemsProvider.Collector,
  ) {
    val context = currentCoroutineContext()
    var attempt = 0
    while (true) {
      attempt++
      try {
        collectItemsOnce(provider, params, operationDisposable, collector)
        return
      }
      catch (e: ProcessCanceledException) {
        if (!context.isActive) {
          throw e
        }
      }
      catch (e: CancellationException) {
        if (!context.isActive) {
          throw e
        }
      }

      SeLog.log(SeLog.ITEM_EMIT) {
        "Retrying item collection from ${provider.id}($logLabel) after canceled attempt #$attempt"
      }
      yield()
    }
  }

  private suspend fun collectItemsOnce(
    provider: SeItemsProvider,
    params: SeParams,
    operationDisposable: Disposable?,
    collector: SeItemsProvider.Collector,
  ) {
    if (provider is SeItemsProviderWithPossibleOperationDisposable && operationDisposable != null) {
      provider.collectItemsWithOperationLifetime(params, operationDisposable, collector)
    }
    else {
      provider.collectItems(params, collector)
    }
  }

  //// Rider ShowInUsagesView needs to prolong a union lifetime in its internals so that it could display the
  //// usages in the find tool window :( sorry, the code is very legacy and hard to redesign
  @ApiStatus.Internal
  @OptIn(ExperimentalAtomicApi::class)
  fun getRawItemsWithOperationLifetime(params: SeParams, operationDisposable: Disposable): Flow<SeItem> {
    if (provider.shouldTreatQueryAsCommandWithArg(params.inputQuery) && !provider.areCommandsSupported()) {
      return emptyFlow()
    }
    return itemsRawChannelFlow(params, operationDisposable).map { it.item }
  }

  fun getRawItems(params: SeParams): Flow<SeItem> = getOwnedRawItems(params).map { it.item }

  private fun getOwnedRawItems(params: SeParams): Flow<OwnedItem> {
    if (provider.shouldTreatQueryAsCommandWithArg(params.inputQuery) && !provider.areCommandsSupported()) {
      return emptyFlow()
    }

    return itemsRawChannelFlow(params, operationDisposable = null)
  }

  suspend fun itemSelected(
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return providerOf(itemData).itemSelected(item, modifiers, searchText)
  }

  suspend fun getSearchScopesInfo(): SearchScopesInfo? = provider.getSearchScopesInfo()

  suspend fun getTypeVisibilityStates(key: String): List<SeTypeVisibilityStatePresentation>? = provider.getTypeVisibilityStates(key)

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean {
    return provider.canBeShownInFindResults()
  }

  suspend fun performExtendedAction(itemData: SeItemData): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    val provider = providerOf(itemData)
    return withContext(Dispatchers.EDT) {
      provider.performExtendedAction(item)
    }
  }

  suspend fun getPreviewInfo(itemData: SeItemData, project: Project): SePreviewInfo? {
    val item = itemData.fetchItemIfExists() ?: return null

    // An item of another search project gets its preview from the provider and the project that found it.
    val owner = searchProjectProviderOf(itemData)
    return ((owner?.provider ?: provider) as? SeItemsPreviewProvider)?.getPreviewInfo(item, owner?.project ?: project)
  }

  override fun dispose() {
    SeLog.log(SeLog.LIFE_CYCLE, "$logLabel provider ${id.value} disposed")
    Disposer.dispose(provider)
  }
}

/** A found [item] and the key of the search project that gave it, see [SeItemDataKeys.SEARCH_PROJECT]. */
private data class OwnedItem(val item: SeItem, val searchProjectKey: String?)
