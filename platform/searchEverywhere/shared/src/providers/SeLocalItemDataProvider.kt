// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@ApiStatus.Internal
class SeLocalItemDataProvider(
  private val provider: SeItemsProvider,
  private val sessionRef: DurableRef<SeSessionEntity>,
  private val logLabel: String = "Local",
) : Disposable {
  val id: SeProviderId
    get() = SeProviderId(provider.id)
  val displayName: @Nls String
    get() = provider.displayName

  private val infoWithReportableId = mapOf(
    SeItemDataKeys.REPORTABLE_PROVIDER_ID to
      (if (SearchEverywhereUsageTriggerCollector.isReportable(provider)) provider.id else SearchEverywhereUsageTriggerCollector.NOT_REPORTABLE_ID)
  )

  @OptIn(ExperimentalAtomicApi::class)
  fun getItems(params: SeParams): Flow<SeItemData> {
    val counter = AtomicInt(0)
    return getRawItems(params).mapNotNull { item ->
      val itemData = SeItemData.createItemData(sessionRef, UUID.randomUUID().toString(), item, id, item.weight(), item.presentation(), infoWithReportableId, emptyList())
      itemData?.also {
        SeLog.log(SeLog.ITEM_EMIT) {
          val count = counter.incrementAndFetch()
          "$logLabel provider for ${id.value} receives (total=$count, priority=${itemData.weight}): ${itemData.uuid} - ${itemData.presentation.text.split("\n").firstOrNull()}"
        }
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  @OptIn(ExperimentalAtomicApi::class)
  fun getRawItems(params: SeParams): Flow<SeItem> {
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

  override fun dispose() {
    SeLog.log(SeLog.LIFE_CYCLE, "$logLabel provider ${id.value} disposed")
    Disposer.dispose(provider)
  }
}