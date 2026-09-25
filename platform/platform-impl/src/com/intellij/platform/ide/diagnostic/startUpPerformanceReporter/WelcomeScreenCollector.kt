// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.createDurationField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.util.containers.ComparatorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@Volatile
private var statsIsWritten = false

internal object WelcomeScreenCollector {
  private val channel = Channel<Event>(Int.MAX_VALUE)

  private sealed interface Event {
    /**
     * It's an event that corresponds to a FUS event and does not stop handling of events.
     * See their list at https://youtrack.jetbrains.com/issue/IJPL-269
     */
    sealed interface FUSReportableEvent : Event
  }

  fun close() {
    channel.close()
  }

  private data class LastHandledEvent(val event: Event.FUSReportableEvent, val durationReportedToFUS: Duration)

  suspend fun startWritingStatistics() {
    withContext(Dispatchers.IO) {
      try {
        //ensures non-thread-safe structures work correctly on different threads
        Mutex().withLock {
          doHandleStatisticEvents()
        }
      }
      finally {
        statsIsWritten = true
        channel.close()
      }
    }
  }

  // Is supposed to be invoked from [startWritingStatistics] only.
  // Runs under mutex lock to ensure safe usage of non-thread-safe maps
  private suspend fun doHandleStatisticEvents() {
    for (event in channel) {
      yield()
    }
  }

  private fun getDurationFromStart(
    finishTimestampNano: Long = System.nanoTime(),
    lastReportedEvent: LastHandledEvent?,
  ): Duration {
    val duration = (finishTimestampNano - StartUpMeasurer.getStartTime()).toDuration(DurationUnit.NANOSECONDS)
    return if (lastReportedEvent == null) duration else ComparatorUtil.max(duration, lastReportedEvent.durationReportedToFUS)
  }

  fun reportOldWelcomeScreenEvent(
    welcomeScreedDurationForFUS: Duration,
    splashScreenFUSDuration: Duration?,
  ) {
    if (splashScreenFUSDuration == null) {
      WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(false))
    }
    else {
      WELCOME_SCREEN_EVENT.log(DURATION.with(welcomeScreedDurationForFUS), SPLASH_SCREEN_WAS_SHOWN.with(true),
                               SPLASH_SCREEN_VISIBLE_DURATION.with(splashScreenFUSDuration))
    }
  }
}

private val WELCOME_SCREEN_GROUP = EventLogGroup("welcome.screen.startup.performance", 1)

private val SPLASH_SCREEN_WAS_SHOWN = EventFields.Boolean("splash_screen_was_shown")
private val SPLASH_SCREEN_VISIBLE_DURATION = createDurationField(DurationUnit.MILLISECONDS, "splash_screen_became_visible_duration_ms")
private val DURATION = createDurationField(DurationUnit.MILLISECONDS, "duration_ms")
private val WELCOME_SCREEN_EVENT = WELCOME_SCREEN_GROUP.registerVarargEvent(
  "welcome.screen.shown",
  DURATION, SPLASH_SCREEN_WAS_SHOWN, SPLASH_SCREEN_VISIBLE_DURATION,
)

internal class WelcomeScreenPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = WELCOME_SCREEN_GROUP
}
