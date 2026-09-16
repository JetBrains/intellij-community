// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.frontend

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.impl.RemoteTopicApi
import com.intellij.platform.rpc.topics.impl.RemoteTopicEventDto
import com.intellij.platform.rpc.topics.impl.RemoteTopicListenerIndex
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private val LOG = logger<FrontendRemoteTopicListenersRegistry>()

/**
 * Preloaded application service which starts subscription on both [com.intellij.platform.rpc.topics.ApplicationRemoteTopic] and [com.intellij.platform.rpc.topics.ProjectRemoteTopic]
 * using [com.intellij.platform.rpc.topics.impl.RemoteTopicApi]
 * sending its events to [ApplicationRemoteTopicListener] and [com.intellij.platform.rpc.topics.ProjectRemoteTopicListener] accordingly.
 *
 * The listeners of a topic come from [RemoteTopicListenerIndex], so a listener is constructed on the first event of its topic.
 */
internal class FrontendRemoteTopicListenersRegistry(cs: CoroutineScope) {
  init {
    cs.launch {
      durable {
        RemoteTopicApi.awaitInstance().subscribe().collect { eventDto ->
          try {
            handleEvent(eventDto)
          }
          catch (e: Throwable) {
            rethrowControlFlowException(e)
            LOG.warn("Error during remote topic event handling. Event dto: $eventDto", e)
          }
        }
      }
    }
  }

  private suspend fun handleEvent(eventDto: RemoteTopicEventDto) {
    val index = RemoteTopicListenerIndex.getInstance()
    val projectId = eventDto.projectId
    if (projectId == null) {
      for (listener in index.applicationListeners(eventDto.topicId)) {
        listener.handleEvent(eventDto)
      }
    }
    else {
      val project = projectId.findProjectOrNull() ?: return
      val projectListeners = index.projectListeners(eventDto.topicId)
      if (projectListeners.isEmpty()) return
      project.service<FrontendProjectRemoteTopicListenersRegistry>().handleEvent(eventDto, projectListeners)
    }
  }

  private fun <E : Any> ApplicationRemoteTopicListener<E>.handleEvent(eventDto: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent((eventDto.localEvent ?: eventDto.serializedEvent.get(topic.serializer)) as E)
  }
}

@Service(Service.Level.PROJECT)
private class FrontendProjectRemoteTopicListenersRegistry(private val project: Project, private val cs: CoroutineScope) {
  // handles event in the [project] scope, so we ensure that the project is available in the events handler.
  suspend fun handleEvent(eventDto: RemoteTopicEventDto, listeners: List<ProjectRemoteTopicListener<*>>) {
    supervisorScope {
      cs.async {
        for (listener in listeners) {
          listener.handleEvent(eventDto)
        }
      }.await()
    }
  }

  private fun <E : Any> ProjectRemoteTopicListener<E>.handleEvent(eventDto: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent(project, (eventDto.localEvent ?: eventDto.serializedEvent.get(topic.serializer)) as E)
  }
}
