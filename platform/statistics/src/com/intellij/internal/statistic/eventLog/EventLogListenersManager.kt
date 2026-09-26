// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.containers.MultiMap
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class EventLogListenersManager(coroutineScope: CoroutineScope) {
  companion object {
    private val logger = logger<EventLogListenersManager>()

    private const val JCP_LISTENER_CLASS = "com.intellij.ml.llm.core.statistics.fus.jcp.JcpEventsInterceptor"
    private const val ANDROID_STUDIO_LISTENER_CLASS = "com.android.tools.idea.stats.AndroidStudioEventLoggerListener"
  }

  /** How a listener gets the event data of one recorder before the validation. */
  private enum class RawDataMode {
    /** The whole raw data, with the JCP payload. */
    WITH_JCP_PAYLOAD,

    /** The raw data without the JCP payload. */
    WITHOUT_JCP_PAYLOAD,
  }

  private val subscribers = MultiMap.createConcurrent<String, StatisticsEventLogListener>()
  // Provider class name -> recorder id -> the listener that the provider supplied for that recorder.
  private val listenersFromEP = ConcurrentCollectionFactory.createConcurrentMap<String, MutableMap<String, StatisticsEventLogListener>>()
  private val rawDataModesByRecorder = ConcurrentCollectionFactory.createConcurrentMap<String, Map<StatisticsEventLogListener, RawDataMode>>()

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

      ExternalEventLogListenerProviderExtension.EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<ExternalEventLogListenerProviderExtension> {
        override fun extensionAdded(extension: ExternalEventLogListenerProviderExtension, pluginDescriptor: PluginDescriptor) =
          subscribeFromExtension(extension)

        override fun extensionRemoved(extension: ExternalEventLogListenerProviderExtension, pluginDescriptor: PluginDescriptor) =
          unsubscribeExtension(extension)
      })
    }
  }

  private fun subscribeFromExtension(listenerProvider: ExternalEventLogListenerProvider) {
    // Do not filter providers by the isLoggingAlwaysActive flag as it can be dynamic.
    // A subscription alone delivers no event, because only an active logger notifies a subscriber.
    StatisticsEventLogProviderUtil.getEventLogProviders().forEach { loggerProvider ->
      val recorderId = loggerProvider.recorderId
      listenerProvider.getEventLogListener(recorderId)?.let { eventLogListener ->
        val listeners = listenersFromEP.computeIfAbsent(listenerProvider.javaClass.name) {
          ConcurrentCollectionFactory.createConcurrentMap()
        }
        listeners[recorderId] = eventLogListener
        subscribe(eventLogListener, recorderId)
      }
    }
  }

  private fun unsubscribeExtension(listenerProvider: ExternalEventLogListenerProvider) {
    // Remove each listener from the recorder that it got, because a provider can supply one listener
    // for each recorder.
    val listeners = listenersFromEP.remove(listenerProvider.javaClass.name) ?: return
    listeners.forEach { (recorderId, listener) -> unsubscribe(listener, recorderId) }
  }

  fun notifySubscribers(recorderId: String, validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?, isFromLocalRecorder: Boolean) {
    val testMode = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
    val effectiveRawEventId = if (testMode) rawEventId else null
    val effectiveRawData = if (testMode) rawData.withoutJcpPayload() else null

    val rawDataModes = rawDataModes(recorderId)
    val listeners = subscribers[recorderId]
    for (listener in listeners) {
      try {
        if (!isFromLocalRecorder || isLocalAllowed(listener)) {
          when (rawDataModes[listener]) {
            RawDataMode.WITH_JCP_PAYLOAD -> listener.onLogEvent(validatedEvent, rawEventId, rawData)
            RawDataMode.WITHOUT_JCP_PAYLOAD -> listener.onLogEvent(validatedEvent, rawEventId, rawData.withoutJcpPayload())
            null -> listener.onLogEvent(validatedEvent, effectiveRawEventId, effectiveRawData)
          }
        }
      } catch (e: Exception) {
        logger.warnInProduction(e)
      }
      catch (e: LinkageError) {
        // a broken listener classpath must not abort logging for the other listeners and the recorder
        logger.warnInProduction(e)
      }
    }
  }

  private fun isLocalAllowed(listener: StatisticsEventLogListener): Boolean {
    return listener.javaClass.name == "com.intellij.ae.database.core.baseEvents.fus.Listener"
  }

  /**
   * Tells whether a listener of [recorderId] takes the event data before the validation, so the raw payload is worth
   * carrying to it.
   * The result is cached and recomputed only when subscriptions change, making it cheap to call while logging.
   */
  fun hasRawDataListener(recorderId: String): Boolean {
    return rawDataModes(recorderId).isNotEmpty()
  }

  /**
   * Tells how each listener of [recorderId] takes the event data before the validation.
   * A listener outside the result takes the validated event only, except in the fus test mode.
   *
   * The result is cached and recomputed only when subscriptions change, so the logging path makes one lookup for each
   * event.
   */
  private fun rawDataModes(recorderId: String): Map<StatisticsEventLogListener, RawDataMode> {
    return rawDataModesByRecorder.getOrPut(recorderId) {
      subscribers[recorderId].mapNotNull { listener ->
        rawDataMode(listener, recorderId)?.let { listener to it }
      }.toMap()
    }
  }

  /**
   * Tells how [listener] takes the event data of [recorderId] before the validation, or null when it takes the
   * validated event only.
   *
   * This is an exception, and the platform decides it here. A listener does not ask for the raw data, because the raw
   * data can hold a value that the validation rejects.
   *
   * - The JCP listener is a part of the product, and it takes each recorder that its provider gives a listener for.
   * - The Android Studio listener keeps the behaviour of the event logger that Android Studio used before, for the FUS
   *   recorder only. Android Studio stays responsible for the data that it sends.
   *
   * Each class must also come from a JetBrains plugin.
   */
  private fun rawDataMode(listener: StatisticsEventLogListener, recorderId: String): RawDataMode? {
    val mode = when (listener.javaClass.name) {
      JCP_LISTENER_CLASS -> RawDataMode.WITH_JCP_PAYLOAD
      ANDROID_STUDIO_LISTENER_CLASS -> if (recorderId == FUS_RECORDER) RawDataMode.WITHOUT_JCP_PAYLOAD else null
      else -> null
    }
    return if (mode != null && getPluginInfo(listener.javaClass).isDevelopedByJetBrains()) mode else null
  }

  /**
   * Returns a copy of the raw data without the JCP payload, because that payload goes to the JCP listener only.
   * Returns the map itself when the map holds no payload, so a common event makes no copy.
   */
  private fun Map<String, Any>?.withoutJcpPayload(): Map<String, Any>? {
    if (this == null || !containsKey(FeatureUsageData.JCP_DATA_KEY)) {
      return this
    }
    return HashMap(this).apply { remove(FeatureUsageData.JCP_DATA_KEY) }
  }

  fun subscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    if (!getPluginInfo(subscriber.javaClass).isDevelopedByJetBrains()) return

    subscribers.putValue(recorderId, subscriber)
    rawDataModesByRecorder.remove(recorderId)
  }

  fun unsubscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    subscribers.remove(recorderId, subscriber)
    rawDataModesByRecorder.remove(recorderId)
  }
}

interface StatisticsEventLogListener {
  /**
   * @param rawEventId Event id before validation.
   * @param rawData Event data before validation.
   *
   * [rawEventId] and [rawData] should be used only for testing purpose, so available only in fus test mode, otherwise will be null.
   * A small list of listeners in `EventLogListenersManager` also gets them outside the test mode, for a named recorder.
   * The platform keeps that list, so a listener cannot ask for the raw data.
   */
  fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?)
}