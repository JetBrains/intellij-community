// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.commands.SeCommandItem
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.to

@ApiStatus.Internal
class SeLocalItemDataProvider(
  val provider: SeItemsProvider,
  private val session: SeSession,
  private val logLabel: String = "Local",
) : Disposable {
  val id: SeProviderId
    get() = SeProviderId(provider.id)
  val displayName: @Nls String
    get() = provider.displayName
  val isAdapted: Boolean
    get() = provider is SeAdaptedItemsProvider

  private val infoWithReportableId = mapOf(
    SeItemDataKeys.REPORTABLE_PROVIDER_ID to
      if (SearchEverywhereUsageTriggerCollector.isReportable(provider)) provider.id else SearchEverywhereUsageTriggerCollector.NOT_REPORTABLE_ID
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
    val supportedCommands = getSupportedCommands()
    val commandItems = getCommandItems(params, supportedCommands)

    for (item in commandItems) {
      SeItemData.createItemData(session, UUID.randomUUID().toString(), item, id, item.weight(),
                                item.presentation(), mapOf(SeItemDataKeys.IS_COMMAND to "true"), emptyList()
      )?.also { itemData ->
        val count = counter.incrementAndFetch()
        SeLog.log(SeLog.ITEM_EMIT) {
          "$logLabel provider for ${id.value} receives command (total=$count): " +
          "${itemData.presentation.text.split("\n").firstOrNull()}"
        }
        send(itemData)
      }
    }
  }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)

  private fun getCommandItems(params: SeParams, supportedCommands: List<SeCommandInfo>): List<SeCommandItem> {
    val inputQuery = params.inputQuery
    if (!inputQuery.isCommandQuery) return emptyList()

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
  ): Flow<SeItemData> = getRawItems(params).mapNotNull { item ->
    val itemData = SeItemData.createItemData(session, UUID.randomUUID().toString(), item, id, item.weight(), item.presentation(), infoWithReportableId, emptyList())
    itemData?.also {
      SeLog.log(SeLog.ITEM_EMIT) {
        val count = counter.incrementAndFetch()
        "$logLabel provider for ${id.value} receives (total=$count, priority=${itemData.weight}): ${itemData.uuid} - ${itemData.presentation.text.split("\n").firstOrNull()}"
      }
    }
  }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)

  @OptIn(ExperimentalAtomicApi::class)
  fun getRawItems(params: SeParams): Flow<SeItem> {
    if (params.inputQuery.isCommandWithArgs && !isCommandsSupported()) {
      return emptyFlow()
    }

    return channelFlow {
      try {
        provider.collectItems(params) { item ->
          send(item)
          coroutineContext.isActive
        }
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        SeLog.warn("Error while collecting items from ${provider.id}($logLabel) provider: ${e.message}")
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).onCompletion {
      SeLog.log(SeLog.ITEM_EMIT) { "Item data provider flow completed - $logLabel - ${id.value}" }
    }
  }

  suspend fun itemSelected(
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return provider.itemSelected(item, modifiers, searchText)
  }

  suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    return (provider as? SeSearchScopesProvider)?.getSearchScopesInfo()
  }

  suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation>? =
    (provider as? SeTypeVisibilityStateProvider)?.getTypeVisibilityStates(index)

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean {
    return provider.canBeShownInFindResults()
  }

  suspend fun performExtendedAction(itemData: SeItemData): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return withContext(Dispatchers.EDT) {
      provider.performExtendedAction(item)
    }
  }

  suspend fun getPreviewInfo(itemData: SeItemData, project: Project): SePreviewInfo? {
    val item = itemData.fetchItemIfExists() ?: return null

    return (provider as? SeItemsPreviewProvider)?.getPreviewInfo(item, project)
  }

  fun isPreviewEnabled(): Boolean {
    if (provider is SeAdaptedItemsProvider) {
      return provider.isPreviewProvider()
    }
    return provider is SeItemsPreviewProvider
  }

  fun isExtendedInfoEnabled(): Boolean {
    if (provider is SeAdaptedItemsProvider) {
      return provider.isExtendedInfoProvider()
    }
    return provider is SeExtendedInfoProvider
  }

  fun isCommandsSupported(): Boolean {
    if (provider is SeAdaptedItemsProvider) {
      return provider.isCommandsSupported()
    }
    return provider is SeCommandsProviderInterface
  }

  fun getSupportedCommands(): List<SeCommandInfo> {
    if (provider is SeAdaptedItemsProvider) {
      return provider.getSupportedCommands()
    }
    return (provider as? SeCommandsProviderInterface)?.getSupportedCommands() ?: emptyList()
  }

  override fun dispose() {
    SeLog.log(SeLog.LIFE_CYCLE, "$logLabel provider ${id.value} disposed")
    Disposer.dispose(provider)
  }
}

private val String.isCommandQuery get() = startsWith(SearchTopHitProvider.getTopHitAccelerator()) && !contains(" ")
private val String.isCommandWithArgs get() = startsWith(SearchTopHitProvider.getTopHitAccelerator()) && contains(" ")