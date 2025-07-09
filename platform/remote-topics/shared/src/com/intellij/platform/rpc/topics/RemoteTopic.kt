// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.codeWithMe.ClientId
import com.intellij.platform.rpc.topics.impl.RemoteTopicImpl
import com.intellij.platform.rpc.topics.impl.RemoteTopicSubscribersManager
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

/**
 * Allows sending events from the backend to the frontend clients in Remote Development.
 *
 * Use [sendToClient] for sending events to a specific client or [broadcast] for all clients.
 * The frontend may subscribe to events using [RemoteTopicListener].
 *
 * Example of sending events:
 * ```kotlin
 * // shared
 * @Serializable
 * data class MyEvent(val message: String)
 *
 * val TOPIC = RemoteTopic("SomeRemoteTopic", MyEvent.serializer())
 *
 * // backend (or shared)
 * TOPIC.sendToClient(MyEvent("test"), clientId)
 * TOPIC.broadcast(MyEvent("all"))
 * ```
 *
 * Important notes:
 *   * [kotlinx.coroutines.flow.Flow], [kotlinx.coroutines.channels.Channel], etc. stateful types are not supported as event payloads.
 *   * In local mode (Monolith) events will be sent normally to all the registered [RemoteTopicListener]s.
 *   * If some listeners are registered on the backend side, they also will be triggered on an event.
 *   * If the frontend sends event to the [RemoteTopic], its listeners will be triggered as well,
 *       but the backend listeners **won't be triggered**.
 *   * The client will receive the event only if it is connected to the server.
 *       So, if the client is not yet connected, but an event is sent, the event won't be received.
 *
 * @see RemoteTopicListener
 */
@ApiStatus.NonExtendable
interface RemoteTopic<E : Any> {
  /**
   * Topic identifier that should be unique within the application.
   */
  val id: String

  /**
   * Serializer for topic events.
   */
  val serializer: KSerializer<E>
}

/**
 * Creates a new RemoteTopic instance.
 *
 * @param id topic identifier that should be unique within the application.
 * @param serializer kotlinx serializer for RemoteTopic events (typically use `Event.serializer()` to acquire it)
 */
fun <E : Any> RemoteTopic(id: String, serializer: KSerializer<E>): RemoteTopic<E> {
  return RemoteTopicImpl(id, serializer)
}

/**
 * Sends the [event] to a specific client identified by [clientId].
 * If no [clientId] is provided, [ClientId] from current context will be used.
 */
fun <E : Any, T : RemoteTopic<E>> T.sendToClient(event: E, clientId: ClientId = ClientId.current) {
  RemoteTopicSubscribersManager.getInstance().sendEvent(this, event, clientId)
}

/**
 * Broadcasts the [event] to all connected clients.
 */
fun <E : Any, T : RemoteTopic<E>> T.broadcast(event: E) {
  RemoteTopicSubscribersManager.getInstance().broadcastEvent(this, event)
}