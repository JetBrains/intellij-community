// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
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
          send(itemData)
          return coroutineContext.isActive
        }
      })
    }
  }

  override suspend fun itemSelected(itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    val item = itemData.fetchItemIfExists() ?: return false
    return provider.itemSelected(item, modifiers, searchText)
  }
}