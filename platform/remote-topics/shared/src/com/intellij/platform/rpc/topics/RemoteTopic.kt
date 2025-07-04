// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.codeWithMe.ClientId
import com.intellij.platform.rpc.topics.impl.RemoteTopicImpl
import com.intellij.platform.rpc.topics.impl.RemoteTopicSubscribersManager
import kotlinx.serialization.KSerializer

interface RemoteTopic<E : Any> {
  val id: String

  val serializer: KSerializer<E>
}

fun <E : Any> RemoteTopic(id: String, serializer: KSerializer<E>): RemoteTopic<E> {
  return RemoteTopicImpl(id, serializer)
}

fun <E : Any, T : RemoteTopic<E>> T.sendToClient(event: E, clientId: ClientId = ClientId.current) {
  RemoteTopicSubscribersManager.getInstance().sendEvent(this, event, clientId)
}

fun <E : Any, T : RemoteTopic<E>> T.broadcast(event: E) {
  RemoteTopicSubscribersManager.getInstance().broadcastEvent(this, event)
}