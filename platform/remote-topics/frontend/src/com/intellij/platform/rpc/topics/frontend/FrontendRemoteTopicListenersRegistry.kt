// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.impl.RemoteTopicApi
import com.intellij.platform.rpc.topics.impl.RemoteTopicEventDto
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<FrontendRemoteTopicListenersRegistry>()

/**
 * Preloaded application service which starts subscription on both [com.intellij.platform.rpc.topics.ApplicationRemoteTopic] and [com.intellij.platform.rpc.topics.ProjectRemoteTopic]
 * using [com.intellij.platform.rpc.topics.impl.RemoteTopicApi]
 * sending its events to [ApplicationRemoteTopicListener] and [com.intellij.platform.rpc.topics.ProjectRemoteTopicListener] accordingly.
 */
internal class FrontendRemoteTopicListenersRegistry(cs: CoroutineScope) {
  private val applicationTopicsListeners = ConcurrentHashMap<String, MutableSet<ApplicationRemoteTopicListener<*>>>()
  private val projectTopicsListeners = ConcurrentHashMap<String, MutableSet<ProjectRemoteTopicListener<*>>>()

  init {
    cs.launch {
      durable {
        RemoteTopicApi.getInstance().subscribe().collect { eventDto ->
          runCatching {
            if (eventDto.projectId == null) {
              val appListeners = applicationTopicsListeners[eventDto.topicId]
              if (appListeners != null) {
                for (listener in appListeners) {
                  listener.handleEvent(eventDto)
                }
              }
            }
            else {
              val project = eventDto.projectId!!.findProjectOrNull() ?: return@runCatching
              val projectListeners = projectTopicsListeners[eventDto.topicId] ?: return@runCatching
              project.service<FrontendProjectRemoteTopicListenersRegistry>().handleEvent(eventDto, projectListeners)
            }
          }.onFailure { e ->
            LOG.warn("Error during remote topic event handling. Event dto: $eventDto", e)
          }
        }
      }
    }

    // Register listeners for ApplicationRemoteTopicListener
    ApplicationRemoteTopicListener.EP_NAME.addExtensionPointListener(cs, object : ExtensionPointListener<ApplicationRemoteTopicListener<*>> {
      override fun extensionAdded(extension: ApplicationRemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        addNewApplicationListener(extension)
      }

      override fun extensionRemoved(extension: ApplicationRemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        applicationTopicsListeners[extension.topic.id]?.remove(extension)
      }
    })

    for (extension in ApplicationRemoteTopicListener.EP_NAME.extensionList) {
      addNewApplicationListener(extension)
    }

    // Register listeners for ProjectRemoteTopicListener
    ProjectRemoteTopicListener.EP_NAME.addExtensionPointListener(cs, object : ExtensionPointListener<ProjectRemoteTopicListener<*>> {
      override fun extensionAdded(extension: ProjectRemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        addNewProjectListener(extension)
      }

      override fun extensionRemoved(extension: ProjectRemoteTopicListener<*>, pluginDescriptor: PluginDescriptor) {
        projectTopicsListeners[extension.topic.id]?.remove(extension)
      }
    })

    for (extension in ProjectRemoteTopicListener.EP_NAME.extensionList) {
      addNewProjectListener(extension)
    }
  }

  private fun <E : Any> ApplicationRemoteTopicListener<E>.handleEvent(eventDto: RemoteTopicEventDto) {
    @Suppress("UNCHECKED_CAST")
    handleEvent((eventDto.localEvent ?: eventDto.serializedEvent.get(topic.serializer)) as E)
  }

  private fun <E : Any> addNewApplicationListener(listener: ApplicationRemoteTopicListener<E>) {
    applicationTopicsListeners.computeIfAbsent(listener.topic.id) { ConcurrentHashMap.newKeySet() }.add(listener)
  }

  private fun <E : Any> addNewProjectListener(listener: ProjectRemoteTopicListener<E>) {
    projectTopicsListeners.computeIfAbsent(listener.topic.id) { ConcurrentHashMap.newKeySet() }.add(listener)
  }
}

@Service(Service.Level.PROJECT)
private class FrontendProjectRemoteTopicListenersRegistry(private val project: Project, private val cs: CoroutineScope) {
  // handles event in the [project] scope, so we ensure that the project is available in the events handler.
  suspend fun handleEvent(eventDto: RemoteTopicEventDto, listeners: Set<ProjectRemoteTopicListener<*>>) {
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