// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.containers.MultiMap
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class EventLogListenersManager {
  private val subscribers = MultiMap.createConcurrent<String, StatisticsEventLogListener>()
  private var listenersFromEP = ConcurrentCollectionFactory.createConcurrentMap<String, StatisticsEventLogListener>()

  init {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogSettings.EP_NAME)) {
      ExternalEventLogSettings.EP_NAME.extensionList.forEach { subscribeFromExtension(it) }

      // Support for dynamic plugin
      ExternalEventLogSettings.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ExternalEventLogSettings> {
        override fun extensionAdded(extension: ExternalEventLogSettings, pluginDescriptor: PluginDescriptor) =
          subscribeFromExtension(extension)

        override fun extensionRemoved(extension: ExternalEventLogSettings, pluginDescriptor: PluginDescriptor) =
          unsubscribeExtension(extension)
      })
    }

    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogListenerProviderExtension.EP_NAME)) {
      ExternalEventLogListenerProviderExtension.EP_NAME.extensionList.forEach { subscribeFromExtension(it) }

      ExternalEventLogListenerProviderExtension.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ExternalEventLogListenerProviderExtension> {
        override fun extensionAdded(extension: ExternalEventLogListenerProviderExtension, pluginDescriptor: PluginDescriptor) =
          subscribeFromExtension(extension)

        override fun extensionRemoved(extension: ExternalEventLogListenerProviderExtension, pluginDescriptor: PluginDescriptor) =
          unsubscribeExtension(extension)
      })
    }
  }

  private fun subscribeFromExtension(listenerProvider: ExternalEventLogListenerProvider) {
    StatisticsEventLogProviderUtil.getEventLogProviders().filter { it.isLoggingAlwaysActive() }.forEach { loggerProvider ->
      val recorderId = loggerProvider.recorderId
      listenerProvider.getEventLogListener(recorderId)?.let { eventLogListener ->
        listenersFromEP[listenerProvider.javaClass.name] = eventLogListener
        subscribe(eventLogListener, recorderId)
      }
    }
  }

  private fun unsubscribeExtension(listenerProvider: ExternalEventLogListenerProvider) {
    if (listenersFromEP.isEmpty()) return
    val listener = listenersFromEP[listenerProvider.javaClass.name] ?: return
    // Do not filter providers by isForceCollectionEnabled flag as it can be dynamic
    StatisticsEventLogProviderUtil.getEventLogProviders().forEach {
      unsubscribe(listener, it.recorderId)
    }
  }

  fun notifySubscribers(recorderId: String, validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?, isFromLocalRecorder: Boolean) {
    val listeners = subscribers[recorderId]
    for (listener in listeners) {
      if (!isFromLocalRecorder || isLocalAllowed(listener)) {
        listener.onLogEvent(validatedEvent, rawEventId, rawData)
      }
    }
  }

  private fun isLocalAllowed(listener: StatisticsEventLogListener): Boolean {
    return listener.javaClass.name == "com.intellij.ae.database.core.baseEvents.fus.Listener"
  }

  fun subscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    if (!getPluginInfo(subscriber.javaClass).isDevelopedByJetBrains()) return

    subscribers.putValue(recorderId, subscriber)
  }

  fun unsubscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    subscribers.remove(recorderId, subscriber)
  }
}

interface StatisticsEventLogListener {
  /**
   * @param rawEventId Event id before validation.
   * @param rawData Event data before validation.
   *
   * [rawEventId] and [rawData] should be used only for testing purpose, so available only in fus test mode, otherwise will be null.
   */
  fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?)
}