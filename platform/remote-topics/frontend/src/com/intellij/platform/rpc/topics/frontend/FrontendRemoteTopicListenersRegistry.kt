// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.frontend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.rpc.topics.RemoteTopicListener
import com.intellij.platform.rpc.topics.impl.RemoteTopicApi
import com.intellij.platform.rpc.topics.impl.RemoteTopicEventDto
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<FrontendRemoteTopicListenersRegistry>()

/**
 * Preloaded service which starts subscription on [com.intellij.platform.rpc.topics.impl.RemoteTopicApi] sending its events to
 * [RemoteTopicListener]s.
 */
internal class FrontendRemoteTopicListenersRegistry(cs: CoroutineScope) {
  private val topicsListeners = ConcurrentHashMap<String, MutableSet<RemoteTopicListener<*>>>()

  init {
    cs.launch {
      durable {
        RemoteTopicApi.getInstance().subscribe().collect { eventDto ->
          runCatching {
            val topicListeners = topicsListeners[eventDto.topicId] ?: return@collect

            for (listener in topicListeners) {
              listener.handleEvent(eventDto)
            }
          }.onFailure { e ->
            LOG.warn("Error during remote topic event handling. Event dto: $eventDto", e)
          }
        }
      }
    }

    RemoteTopicListener.EP_NAME.addExtensionPointListener(cs, object : ExtensionPointListener<RemoteTopicListener<*>> {
      override fun extensionAdded(extension: RemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        addNewListener(extension)
      }

      override fun extensionRemoved(extension: RemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        topicsListeners[extension.topic.id]?.remove(extension)
      }
    })

    for (extension in RemoteTopicListener.EP_NAME.extensionList) {
      addNewListener(extension)
    }
  }

  private fun <E : Any> RemoteTopicListener<E>.handleEvent(eventDto: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent((eventDto.localEvent ?: eventDto.serializedEvent.get(topic.serializer)) as E)
  }

  private fun <E : Any> addNewListener(listener: RemoteTopicListener<E>) {
    topicsListeners.computeIfAbsent(listener.topic.id) { ConcurrentHashMap.newKeySet() }.add(listener)
  }
}