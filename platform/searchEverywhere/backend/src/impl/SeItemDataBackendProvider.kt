// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.SeLog.LIFE_CYCLE
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemDataBackendProvider(override val id: SeProviderId,
                                private val provider: SeItemsProvider,
                                private val sessionRef: DurableRef<SeSessionEntity>
): SeItemDataProvider {
  override fun getItems(params: SeParams): Flow<SeItemData> {
    return channelFlow {
      provider.collectItems(params, object : SeItemsProvider.Collector {
        override suspend fun put(item: SeItem): Boolean {
          val itemData = SeItemData.createItemData(sessionRef, item, id, item.weight(), item.presentation()) ?: return true
          SeLog.log(ITEM_EMIT) { "Backend provider for ${id.value} sends: ${itemData.presentation.text}" }
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

  override fun dispose() {
    SeLog.log(LIFE_CYCLE, "Backend provider ${id.value} disposed")
    Disposer.dispose(provider)
  }
}