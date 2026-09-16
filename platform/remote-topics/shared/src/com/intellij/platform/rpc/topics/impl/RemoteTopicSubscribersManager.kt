// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.project.projectId
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.RemoteTopic
import fleet.openmap.SerializedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<RemoteTopicSubscribersManager>()

/**
 * Backend's side manager that handles [RemoteTopicApi] subscriptions and send events to them.
 *
 * Also, it handles local [ApplicationRemoteTopic] and [ProjectRemoteTopicListener] subscriptions and sends events to them as well.
 */
@Service(Service.Level.APP)
class RemoteTopicSubscribersManager(cs: CoroutineScope) {
  private val events = Channel<RemoteTopicInternalEvent>(Channel.UNLIMITED)
  private val clients = ConcurrentHashMap<ClientId, (RemoteTopicEventDto) -> Unit>()
  private val connectedClientsStateFlow: MutableStateFlow<Set<ClientId>> = MutableStateFlow(emptySet())

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
    clients[ClientId.localId] = { eventDto ->
      val index = RemoteTopicListenerIndex.getInstance()
      if (eventDto.projectId != null) {
        val project = eventDto.projectId.findProjectOrNull()
        if (project != null) {
          for (listener in index.projectListeners(eventDto.topicId)) {
            handleLocally(listener, eventDto) { listener.handleEventWithProjectLocally(project, eventDto) }
          }
        }
      }
      else {
        for (listener in index.applicationListeners(eventDto.topicId)) {
          handleLocally(listener, eventDto) { listener.handleEventLocally(eventDto) }
        }
      }
    }
    connectedClientsStateFlow.value = clients.keys.toSet()
  }

  private inline fun handleLocally(listener: Any, event: RemoteTopicEventDto, handle: () -> Unit) {
    try {
      handle()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("Error during local remote topic event handling by $listener. Event dto: $event", e)
    }
  }

  private fun <E : Any> ApplicationRemoteTopicListener<E>.handleEventLocally(event: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent(event.localEvent!! as E)
  }

  private fun <E : Any> ProjectRemoteTopicListener<E>.handleEventWithProjectLocally(project: Project, event: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent(project, event.localEvent!! as E)
  }

  fun registerClient(cs: CoroutineScope, clientId: ClientId, onEvent: (RemoteTopicEventDto) -> Unit) {
    val previousCallback = clients.putIfAbsent(clientId, onEvent)
    if (previousCallback == null) {
      connectedClientsStateFlow.value = clients.keys.toSet()
      cs.coroutineContext.job.invokeOnCompletion {
        clients.remove(clientId)
        connectedClientsStateFlow.value = clients.keys.toSet()
      }
    }
  }

  fun <E : Any> sendEvent(topic: RemoteTopic<E>, project: Project?, event: E, clientId: ClientId) {
    events.trySend(RemoteTopicInternalEvent.Client(clientId, createInternalEvent(topic, project, event)))
  }

  fun <E : Any> broadcastEvent(topic: RemoteTopic<E>, project: Project?, event: E) {
    events.trySend(RemoteTopicInternalEvent.Broadcast(createInternalEvent(topic, project, event)))
  }

  suspend fun awaitClientSubscription(clientId: ClientId) {
    connectedClientsStateFlow.first { clientId in it }
  }

  @VisibleForTesting
  fun connectedRemoteClients(): Set<ClientId> {
    return clients.keys.filter { it != ClientId.localId }.toSet()
  }

  private fun <E : Any> createInternalEvent(topic: RemoteTopic<E>, project: Project?, event: E): RemoteTopicEventDto {
    return RemoteTopicEventDto(topic.id, project?.projectId(), event, SerializedValue.fromDeserializedValue(event, topic.serializer))
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
  val projectId: ProjectId?,
  @Transient val localEvent: Any? = null,
  val serializedEvent: SerializedValue,
)