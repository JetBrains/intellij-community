// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@ApiStatus.Internal
class SeLocalItemDataProvider(private val provider: SeItemsProvider,
                              private val sessionRef: DurableRef<SeSessionEntity>,
                              private val logLabel: String = "Local"): Disposable {
  val id: SeProviderId
    get() = SeProviderId(provider.id)
  val displayName: @Nls String
    get() = provider.displayName

  @OptIn(ExperimentalAtomicApi::class)
  fun getItems(params: SeParams, equalityChecker: SeEqualityChecker?): Flow<SeItemData> {
    return channelFlow {
      val counter = AtomicInt(0)

      provider.collectItems(params, object : SeItemsProvider.Collector {
        override suspend fun put(item: SeItem): Boolean {
          val uuid = UUID.randomUUID().toString()
          val uuidToReplace = mutableListOf<String>()

          if (item is SeLegacyItem && equalityChecker != null) {
            val obj = item.rawObject
            val equalityAction = equalityChecker.getAction(obj, uuid, item.weight(), item.contributor)

            when (equalityAction) {
              SeEqualityChecker.Add -> { /* Do nothing, business as usual */ }
              is SeEqualityChecker.Replace -> uuidToReplace.addAll(equalityAction.itemsIds)
              SeEqualityChecker.Skip -> return coroutineContext.isActive
            }
          }

          val itemData = SeItemData.createItemData(
            sessionRef, uuid, item, id, item.weight(), item.presentation(), uuidToReplace
          ) ?: return true

          SeLog.log(SeLog.ITEM_EMIT) {
            val count = counter.incrementAndFetch()
            "$logLabel provider for ${id.value} receives (total=$count, priority=${itemData.weight}): ${itemData.presentation.text.split("\n").firstOrNull()}"
          }
          send(itemData)
          return coroutineContext.isActive
        }
      })
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  suspend fun itemSelected(
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return provider.itemSelected(item, modifiers, searchText)
  }

  suspend fun getSearchScopesInfo(): SeSearchScopesInfo? {
    return (provider as? SeSearchScopesProvider)?.getSearchScopesInfo()
  }

  suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation>? =
    (provider as? SeTypeVisibilityStateProvider)?.getTypeVisibilityStates()

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