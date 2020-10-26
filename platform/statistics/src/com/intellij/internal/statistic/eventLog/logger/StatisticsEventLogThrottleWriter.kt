// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.logger

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.logger.EventsRateResultType.DENIED_TOTAL
import com.intellij.internal.statistic.utils.EventRateThrottleResult
import com.intellij.internal.statistic.utils.EventsIdentityWindowThrottle
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle

class StatisticsEventLogThrottleWriter(private val recorderVersion: String,
                                       private val delegate: StatisticsEventLogWriter): StatisticsEventLogWriter {
  private val ourLock: Any = Object()

  /**
   * Allow up to 24000 events per hour
   */
  private val ourThrottle: EventsRateWindowThrottle = EventsRateWindowThrottle(24000, 60L * 60 * 1000, System.currentTimeMillis())

  /**
   * Allow up to 12000 events per group per hour
   */
  private val ourGroupThrottle: EventsIdentityWindowThrottle = EventsIdentityWindowThrottle(12000, 60L * 60 * 1000)


  override fun log(logEvent: LogEvent) {
    val shouldLog = tryPass(logEvent.group.id, System.currentTimeMillis())
    if (shouldLog.type == EventsRateResultType.ACCEPTED) {
      return delegate.log(logEvent)
    }

    if (shouldLog.report) {
      val errorGroupId = if (shouldLog.type == DENIED_TOTAL) EventLogSystemLogger.GROUP else logEvent.group.id
      val errorGroupVersion = if (shouldLog.type == DENIED_TOTAL) recorderVersion else logEvent.group.version
      val event = LogEvent(
        logEvent.session, logEvent.build, logEvent.bucket, logEvent.time, errorGroupId, errorGroupVersion, logEvent.recorderVersion,
        LogEventAction(EventLogSystemEvents.TOO_MANY_EVENTS, logEvent.event.state)
      )
      return delegate.log(event)
    }
  }

  private fun tryPass(group: String, now: Long): EventsRateResult {
    synchronized(ourLock) {
      val result = ourThrottle.tryPass(now)
      if (result != EventRateThrottleResult.ACCEPT) {
        val report = result == EventRateThrottleResult.DENY_AND_REPORT
        return EventsRateResult(DENIED_TOTAL, report)
      }

      val groupResult = ourGroupThrottle.tryPass(group, now)
      if (groupResult != EventRateThrottleResult.ACCEPT) {
        val report = groupResult == EventRateThrottleResult.DENY_AND_REPORT
        return EventsRateResult(EventsRateResultType.DENIED_GROUP, report)
      }
      return EventsRateResult(EventsRateResultType.ACCEPTED, false)
    }
  }

  override fun getActiveFile(): EventLogFile? = delegate.getActiveFile()

  override fun getLogFilesProvider(): EventLogFilesProvider = delegate.getLogFilesProvider()

  override fun cleanup() = delegate.cleanup()

  override fun rollOver() = delegate.rollOver()
}

private data class EventsRateResult(val type: EventsRateResultType, val report: Boolean)

private enum class EventsRateResultType {
  ACCEPTED, DENIED_TOTAL, DENIED_GROUP
}