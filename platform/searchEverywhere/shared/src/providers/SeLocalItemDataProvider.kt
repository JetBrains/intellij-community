// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeLocalItemDataProvider(private val provider: SeItemsProvider,
                              private val sessionRef: DurableRef<SeSessionEntity>,
                              private val logLabel: String = "Local"
): SeItemDataProvider {
  override val id: SeProviderId
    get() = SeProviderId(provider.id)
  override val displayName: @Nls String
    get() = provider.displayName

  override fun getItems(params: SeParams): Flow<SeItemData> {
    return channelFlow {
      provider.collectItems(params, object : SeItemsProvider.Collector {
        override suspend fun put(item: SeItem): Boolean {
          val itemData = SeItemData.createItemData(sessionRef, item, id, item.weight(), item.presentation()) ?: return true
          SeLog.log(SeLog.ITEM_EMIT) { "$logLabel provider for ${id.value} receives: ${itemData.presentation.text}" }
          send(itemData)
          return coroutineContext.isActive
        }
      })
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  override suspend fun itemSelected(itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return provider.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getSearchScopesInfo(): SeSearchScopesInfo? {
    return (provider as? SeSearchScopesProvider)?.getSearchScopesInfo()
  }

  override suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation>? =
    (provider as? SeTypeVisibilityStateProvider)?.getTypeVisibilityStates()

  override suspend fun canBeShownInFindResults(): Boolean {
    return provider.canBeShownInFindResults()
  }

  override fun dispose() {
    SeLog.log(SeLog.LIFE_CYCLE, "$logLabel provider ${id.value} disposed")
    Disposer.dispose(provider)
  }
}