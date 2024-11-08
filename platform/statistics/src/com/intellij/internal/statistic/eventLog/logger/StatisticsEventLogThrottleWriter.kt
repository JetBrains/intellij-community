// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.logger

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService.EventLogThresholdConfigOptionsListener
import com.intellij.internal.statistic.utils.EventRateThrottleResult
import com.intellij.internal.statistic.utils.EventsIdentityWindowThrottle
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import kotlinx.coroutines.CoroutineScope

class StatisticsEventLogThrottleWriter(configOptionsService: EventLogConfigOptionsService,
                                                private val recorderId: String,
                                                private val recorderVersion: String,
                                                private val delegate: StatisticsEventLogWriter,
                                                coroutineScope: CoroutineScope) : StatisticsEventLogWriter {
  private val ourLock: Any = Object()

  /**
   * Allow up to 24000 events per hour or another threshold loaded from config
   */
  private val ourThrottle: EventsRateWindowThrottle

  /**
   * Allow up to 12000 events per group per hour or another threshold loaded from config
   */
  private val ourGroupThrottle: EventsIdentityWindowThrottle

  init {
    val configOptions = configOptionsService.getOptions(recorderId)
    val threshold = getOrDefault(configOptions.threshold, 24000)
    ourThrottle = EventsRateWindowThrottle(threshold, 60L * 60 * 1000, System.currentTimeMillis())

    val groupThreshold = getOrDefault(configOptions.groupThreshold, 12000)
    val groupAlertThreshold = getOrDefault(configOptions.groupAlertThreshold, 6000)
    ourGroupThrottle = EventsIdentityWindowThrottle(groupThreshold, groupAlertThreshold, 60L * 60 * 1000)

    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(EventLogConfigOptionsService.TOPIC, object : EventLogThresholdConfigOptionsListener(recorderId) {
      override fun onThresholdChanged(newValue: Int) {
        if (newValue > 0) {
          synchronized(ourLock) {
            ourThrottle.setThreshold(newValue)
          }
        }
      }

      override fun onGroupThresholdChanged(newValue: Int) {
        if (newValue > 0) {
          synchronized(ourLock) {
            ourGroupThrottle.setThreshold(newValue)
          }
        }
      }

      override fun onGroupAlertThresholdChanged(newValue: Int) {
        if (newValue > 0) {
          synchronized(ourLock) {
            ourGroupThrottle.setAlertThreshold(newValue)
          }
        }
      }
    })
  }

  private fun getOrDefault(value: Int, defaultValue: Int): Int {
    return if (value > 0) value else defaultValue
  }

  override fun log(logEvent: LogEvent) {
    val shouldLog = tryPass(logEvent.group.id, System.currentTimeMillis())
    if (shouldLog.type == EventsRateResultType.ALERT_GROUP) {
      val alert = copyEvent(EventLogSystemEvents.TOO_MANY_EVENTS_ALERT, logEvent.group.id, logEvent.group.version, logEvent)
      delegate.log(alert)
      return delegate.log(logEvent)
    }

    if (shouldLog.type == EventsRateResultType.ACCEPTED) {
      return delegate.log(logEvent)
    }

    if (shouldLog.report) {
      val errorGroupId = if (shouldLog.type == EventsRateResultType.DENIED_TOTAL)
        StatisticsEventLogProviderUtil.getEventLogProvider(recorderId).eventLogSystemLogger.group.id
      else logEvent.group.id
      val errorGroupVersion = if (shouldLog.type == EventsRateResultType.DENIED_TOTAL) recorderVersion else logEvent.group.version
      val event = copyEvent(EventLogSystemEvents.TOO_MANY_EVENTS, errorGroupId, errorGroupVersion, logEvent)
      return delegate.log(event)
    }
  }

  private fun copyEvent(eventId: String, groupId: String, groupVersion: String, logEvent: LogEvent) = LogEvent(
    logEvent.session, logEvent.build, logEvent.bucket, logEvent.time, LogEventGroup(groupId, groupVersion), logEvent.recorderVersion,
    LogEventAction(eventId, logEvent.event.state)
  )

  private fun tryPass(group: String, now: Long): EventsRateResult {
    synchronized(ourLock) {
      val result = ourThrottle.tryPass(now)
      if (!result.isAccept) {
        val report = result == EventRateThrottleResult.DENY_AND_REPORT
        return EventsRateResult(EventsRateResultType.DENIED_TOTAL, report)
      }

      val groupResult = ourGroupThrottle.tryPass(group, now)
      if (!groupResult.isAccept) {
        val report = groupResult == EventRateThrottleResult.DENY_AND_REPORT
        return EventsRateResult(EventsRateResultType.DENIED_GROUP, report)
      }
      else if (groupResult == EventRateThrottleResult.ALERT) {
        return EventsRateResult(EventsRateResultType.ALERT_GROUP, true)
      }
      return EventsRateResult(EventsRateResultType.ACCEPTED, false)
    }
  }

  override fun getActiveFile(): EventLogFile? = delegate.getActiveFile()

  override fun getLogFilesProvider(): EventLogFilesProvider = delegate.getLogFilesProvider()

  override fun cleanup() = delegate.cleanup()

  override fun rollOver() = delegate.rollOver()

  override fun dispose() = Disposer.dispose(delegate)
}

private data class EventsRateResult(val type: EventsRateResultType, val report: Boolean)

private enum class EventsRateResultType {
  ACCEPTED, ALERT_GROUP, DENIED_TOTAL, DENIED_GROUP
}