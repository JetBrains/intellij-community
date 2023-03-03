// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getExternalEventLogSettings
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
  private var listenerFromEP: StatisticsEventLogListener? = null

  init {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogSettings.EP_NAME)) {
      addListenerFromEP()

      ExternalEventLogSettings.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ExternalEventLogSettings> {
        override fun extensionAdded(extension: ExternalEventLogSettings, pluginDescriptor: PluginDescriptor) {
          addListenerFromEP()
        }

        override fun extensionRemoved(extension: ExternalEventLogSettings, pluginDescriptor: PluginDescriptor) {
          if (listenerFromEP == null) return
          // Do not filter providers by isForceCollectionEnabled flag as it can be dynamic
          StatisticsEventLogProviderUtil.getEventLogProviders().forEach { provider ->
            unsubscribe(listenerFromEP!!, provider.recorderId)
          }
          listenerFromEP = null
        }
      })
    }
  }

  private fun addListenerFromEP() {
    if (listenerFromEP != null) return // Only one EP instance can exist do not bother if listener has been set
    val externalEventLogSettings = getExternalEventLogSettings()
    externalEventLogSettings?.let {
      externalEventLogSettings.eventLogListener?.let { eventLogListener ->
        listenerFromEP = eventLogListener
        StatisticsEventLogProviderUtil.getEventLogProviders().filter { it.isForceCollectionEnabled() }.forEach { provider ->
          subscribe(eventLogListener, provider.recorderId)
        }
      }
    }
  }

  fun notifySubscribers(recorderId: String, validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
    val listeners = subscribers[recorderId]
    for (listener in listeners) {
      listener.onLogEvent(validatedEvent, rawEventId, rawData)
    }
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