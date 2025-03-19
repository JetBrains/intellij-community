// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeItemDataFrontendProvider(private val projectId: ProjectId,
                                 override val id: SeProviderId,
                                 private val sessionRef: DurableRef<SeSessionEntity>,
                                 private val dataContextId: DataContextId?): SeItemDataProvider {
  override fun getItems(params: SeParams): Flow<SeItemData> {
    return channelFlow {
      SeRemoteApi.getInstance().getItems(projectId, sessionRef, id, params, dataContextId).collect {
        SeLog.log(ITEM_EMIT) { "Frontend provider for ${id.value} receives: ${it.presentation.text}" }
        send(it)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  override suspend fun itemSelected(itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    return SeRemoteApi.getInstance().itemSelected(projectId, sessionRef, itemData, modifiers, searchText)
  }
}