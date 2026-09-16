// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<RemoteTopicListenerIndex>()

/**
 * Finds the listeners of a remote topic by its id.
 *
 * A listener declaration carries the topic id in the `topicId` attribute, so the index reads it from the plugin descriptor.
 * The listener class is loaded and the listener is constructed on the first event of its topic.
 * A listener registered without the attribute is constructed on the first event of any topic, because only the instance knows its topic.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class RemoteTopicListenerIndex(cs: CoroutineScope) {
  private val applicationListeners = TopicListenerCache(ApplicationRemoteTopicListener.EP_NAME) { it.topic.id }
  private val projectListeners = TopicListenerCache(ProjectRemoteTopicListener.EP_NAME) { it.topic.id }

  init {
    ApplicationRemoteTopicListener.EP_NAME.addChangeListener(cs) { applicationListeners.clear() }
    ProjectRemoteTopicListener.EP_NAME.addChangeListener(cs) { projectListeners.clear() }
  }

  fun applicationListeners(topicId: String): List<ApplicationRemoteTopicListener<*>> = applicationListeners.get(topicId)

  fun projectListeners(topicId: String): List<ProjectRemoteTopicListener<*>> = projectListeners.get(topicId)

  companion object {
    /** The extension attribute that names the topic a listener handles. */
    const val TOPIC_ID_ATTRIBUTE: String = "topicId"

    fun getInstance(): RemoteTopicListenerIndex = service()
  }
}

private class TopicListenerCache<L : Any>(
  private val epName: ExtensionPointName<L>,
  private val topicIdOf: (L) -> String,
) {
  private val cache = ConcurrentHashMap<String, List<L>>()

  @Volatile
  private var generation = 0

  fun clear() {
    generation++
    cache.clear()
  }

  fun get(topicId: String): List<L> {
    cache[topicId]?.let { return it }
    val generation = generation
    // a listener constructor may run arbitrary code, so it runs outside of the map lock
    val listeners = collect(topicId)
    if (generation == this.generation) {
      cache.putIfAbsent(topicId, listeners)
    }
    return listeners
  }

  private fun collect(topicId: String): List<L> {
    val result = ArrayList<L>()
    for (extension in epName.filterableLazySequence()) {
      val declaredTopicId = extension.getCustomAttribute(RemoteTopicListenerIndex.TOPIC_ID_ATTRIBUTE)
      if (declaredTopicId == null) {
        // an instance registered programmatically, or a declaration the plugin model validator rejects
        val listener = extension.instance ?: continue
        if (topicIdOf(listener) == topicId) {
          result.add(listener)
        }
      }
      else if (declaredTopicId == topicId) {
        val listener = extension.instance ?: continue
        val actualTopicId = topicIdOf(listener)
        if (actualTopicId == topicId) {
          result.add(listener)
        }
        else {
          LOG.error(PluginException(
            "Listener ${extension.implementationClassName} is declared with ${RemoteTopicListenerIndex.TOPIC_ID_ATTRIBUTE}='$declaredTopicId' " +
            "but its topic id is '$actualTopicId'. The listener receives no event until the declaration is fixed.",
            extension.pluginDescriptor.pluginId,
          ))
        }
      }
    }
    return result
  }
}
