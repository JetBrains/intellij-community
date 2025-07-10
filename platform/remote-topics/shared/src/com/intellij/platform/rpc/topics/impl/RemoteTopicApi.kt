// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Internal RPC API for [com.intellij.platform.rpc.topics.RemoteTopic] implementation, so the frontend may subscribe on the backend events.
 */
@Rpc
interface RemoteTopicApi : RemoteApi<Unit> {
  suspend fun subscribe(): Flow<RemoteTopicEventDto>

  companion object {
    @JvmStatic
    suspend fun getInstance(): RemoteTopicApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteTopicApi>())
    }
  }
}