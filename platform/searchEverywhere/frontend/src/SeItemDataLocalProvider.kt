// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.*
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemDataLocalProvider(private val itemsProvider: SeItemsProvider): SeItemDataProvider {
  override val id: SeProviderId
    get() = SeProviderId(itemsProvider.id)

  override fun getItems(sessionRef: DurableRef<SeSessionEntity>, params: SeParams): Flow<SeItemData> {
    return itemsProvider.getItems(params).mapNotNull {
      SeItemData.createItemData(sessionRef, it, id, it.weight(), it.presentation())
    }
  }
}