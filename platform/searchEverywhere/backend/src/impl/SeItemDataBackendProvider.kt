// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.*
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemDataBackendProvider(override val id: SeProviderId,
                                private val provider: SeItemsProvider): SeItemDataProvider {
  override fun getItems(sessionRef: DurableRef<SeSessionEntity>, params: SeParams): Flow<SeItemData> {
    return provider.getItems(params).mapNotNull { item ->
      SeItemData.createItemData(sessionRef, item, id, item.weight(), item.presentation())
    }
  }
}