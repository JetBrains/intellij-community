// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.topics.RemoteTopic

import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
class RemoteTopicSubscribersManager(cs: CoroutineScope) {
  private val events = Channel<RemoteTopicInternalEvent>(Channel.UNLIMITED)
  private val clients = CopyOnWriteArrayList<ConnectedClient>()

  init {
    cs.launch {
      for (event in events) {
        when (event) {
          is RemoteTopicInternalEvent.Broadcast -> {
            for (client in clients) {
              client.onEvent(event.eventDto)
            }
          }
          is RemoteTopicInternalEvent.Client -> {
            val client = clients.find { it.clientId == event.clientId }
            client?.onEvent(event.eventDto)
          }
        }
      }
    }
  }

  fun registerClient(cs: CoroutineScope, clientId: ClientId, onEvent: (RemoteTopicEventDto) -> Unit) {
    val client = ConnectedClient(clientId, onEvent)
    clients.add(client)
    cs.coroutineContext.job.invokeOnCompletion {
      clients.remove(client)
    }
  }

  fun <E : Any, T : RemoteTopic<E>> sendEvent(topic: T, event: E, clientId: ClientId) {
    events.trySend(RemoteTopicInternalEvent.Client(clientId, createInternalEvent(topic, event)))
  }

  fun <E : Any, T : RemoteTopic<E>> broadcastEvent(topic: T, event: E) {
    events.trySend(RemoteTopicInternalEvent.Broadcast(createInternalEvent(topic, event)))
  }

  @VisibleForTesting
  fun connectedClients(): Set<ClientId> {
    return clients.map { it.clientId }.toSet()
  }

  private fun <E : Any, T : RemoteTopic<E>> createInternalEvent(topic: T, event: E): RemoteTopicEventDto {
    return RemoteTopicEventDto(topic.id, event, SerializedValue.fromDeserializedValue(event, topic.serializer))
  }

  private data class ConnectedClient(val clientId: ClientId, val onEvent: (RemoteTopicEventDto) -> Unit)

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