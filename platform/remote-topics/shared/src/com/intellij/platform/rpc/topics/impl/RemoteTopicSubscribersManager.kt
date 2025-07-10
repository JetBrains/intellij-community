// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.platform.rpc.topics.RemoteTopicListener
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/**
 * Backend's side manager that handles [RemoteTopicApi] subscriptions and send [RemoteTopic] events to them.
 *
 * Also, it handles local [RemoteTopicListener] subscriptions and sends events to them as well.
 */
@Service(Service.Level.APP)
class RemoteTopicSubscribersManager(cs: CoroutineScope) {
  private val events = Channel<RemoteTopicInternalEvent>(Channel.UNLIMITED)
  private val clients = ConcurrentHashMap<ClientId, (RemoteTopicEventDto) -> Unit>()

  init {
    registerLocalClient()
    cs.launch {
      for (event in events) {
        when (event) {
          is RemoteTopicInternalEvent.Broadcast -> {
            for ((clientId, clientCallback) in clients) {
              clientCallback(event.eventDto)
            }
          }
          is RemoteTopicInternalEvent.Client -> {
            val clientCallback = clients[event.clientId]
            clientCallback?.invoke(event.eventDto)
          }
        }
      }
    }
  }

  private fun registerLocalClient() {
    clients[ClientId.localId] = {
      RemoteTopicListener.EP_NAME.forEachExtensionSafe { listener ->
        if (listener.topic.id == it.topicId) {
          listener.handleEventLocally(it)
        }
      }
    }
  }

  private fun <E : Any> RemoteTopicListener<E>.handleEventLocally(event: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent(event.localEvent!! as E)
  }

  fun registerClient(cs: CoroutineScope, clientId: ClientId, onEvent: (RemoteTopicEventDto) -> Unit) {
    val previousCallback = clients.putIfAbsent(clientId, onEvent)
    if (previousCallback == null) {
      cs.coroutineContext.job.invokeOnCompletion {
        clients.remove(clientId)
      }
    }
  }

  fun <E : Any, T : RemoteTopic<E>> sendEvent(topic: T, event: E, clientId: ClientId) {
    events.trySend(RemoteTopicInternalEvent.Client(clientId, createInternalEvent(topic, event)))
  }

  fun <E : Any, T : RemoteTopic<E>> broadcastEvent(topic: T, event: E) {
    events.trySend(RemoteTopicInternalEvent.Broadcast(createInternalEvent(topic, event)))
  }

  @VisibleForTesting
  fun connectedRemoteClients(): Set<ClientId> {
    return clients.keys.filter { it != ClientId.localId }.toSet()
  }

  private fun <E : Any, T : RemoteTopic<E>> createInternalEvent(topic: T, event: E): RemoteTopicEventDto {
    return RemoteTopicEventDto(topic.id, event, SerializedValue.fromDeserializedValue(event, topic.serializer))
  }

  private sealed interface RemoteTopicInternalEvent {
    data class Broadcast(val eventDto: RemoteTopicEventDto) : RemoteTopicInternalEvent
    data class Client(val clientId: ClientId, val eventDto: RemoteTopicEventDto) : RemoteTopicInternalEvent
  }

  companion object {
    fun getInstance(): RemoteTopicSubscribersManager = service()
  }
}

@Serializable
data class RemoteTopicEventDto(
  val topicId: String,
  @Transient val localEvent: Any? = null,
  val serializedEvent: SerializedValue,
)