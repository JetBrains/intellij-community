// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.backend

import com.intellij.codeWithMe.ClientId
import com.intellij.platform.rpc.topics.impl.RemoteTopicApi
import com.intellij.platform.rpc.topics.impl.RemoteTopicEventDto
import com.intellij.platform.rpc.topics.impl.RemoteTopicSubscribersManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

internal class BackendRemoteTopicApi : RemoteTopicApi {
  override suspend fun subscribe(): Flow<RemoteTopicEventDto> {
    return channelFlow {
      val cs = this@channelFlow
      val events = Channel<RemoteTopicEventDto>(Channel.UNLIMITED)
      RemoteTopicSubscribersManager.getInstance().registerClient(cs, ClientId.current, onEvent = {
        events.trySend(it)
      })

      for (event in events) {
        send(event)
      }
    }
  }
}