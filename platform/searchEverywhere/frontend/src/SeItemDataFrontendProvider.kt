// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import fleet.kernel.DurableRef
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemDataFrontendProvider(private val projectId: ProjectId,
                                 override val id: SeProviderId,
                                 private val sessionRef: DurableRef<SeSessionEntity>,
                                 private val serializedDataContext: SerializedValue?): SeItemDataProvider {
  override fun getItems(params: SeParams): Flow<SeItemData> {
    return channelFlow {
      SeRemoteApi.getInstance().getItems(projectId, sessionRef, id, params, serializedDataContext).collectLatest {
        send(it)
      }
    }
  }

  override suspend fun itemSelected(itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    return SeRemoteApi.getInstance().itemSelected(projectId, sessionRef, itemData, modifiers, searchText)
  }
}