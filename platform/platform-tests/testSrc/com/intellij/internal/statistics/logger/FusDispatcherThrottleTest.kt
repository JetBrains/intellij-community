// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.dispatcher.IntellijReportValidator
import com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider
import com.intellij.testFramework.HeavyPlatformTestCase
import com.jetbrains.fus.reporting.FusClientConfig
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.HttpResponse
import com.jetbrains.fus.reporting.MessageBus
import com.jetbrains.fus.reporting.REMOTE_CONFIG_OPTIONS_UPDATED
import com.jetbrains.fus.reporting.RegionCode
import com.jetbrains.fus.reporting.RemoteConfig
import com.jetbrains.fus.reporting.defaults.NoOpLoggerFactory
import com.jetbrains.fus.reporting.defaults.dispatcher.EventQueue
import com.jetbrains.fus.reporting.defaults.dispatcher.SendInformationAggregator
import com.jetbrains.fus.reporting.defaults.dispatcher.SendResult
import com.jetbrains.fus.reporting.defaults.dispatcher.SimpleLegacyReportDispatcher
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * End-to-end check that throttling is enforced by the SDK [SimpleLegacyReportDispatcher] (the IntelliJ-side
 * `StatisticsEventLogThrottleWriter` was removed in AP-7777). Verifies that once the per-group quota is exceeded the
 * dispatcher emits a [EventLogSystemEvents.TOO_MANY_EVENTS] system event, exactly as the old throttle writer did.
 *
 * The dispatcher is built by hand (production wiring goes through FusClient/FusComponentProvider). A fake [EventQueue]
 * captures everything the dispatcher enqueues after merge → throttle → validate, so no file I/O or serialization is
 * involved. In unit-test mode the blind validator passes events through unchanged, so the synthetic `TOO_MANY_EVENTS`
 * event keeps its id.
 */
class FusDispatcherThrottleTest : HeavyPlatformTestCase() {
  @Test
  fun testThrottleEmitsTooManyEventsWhenGroupQuotaExceeded() {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    try {
      val recorderId = "THROTTLE_TEST"
      val messageBus = MessageBus(scope)
      val eventQueue = RecordingEventQueue()
      // queueEvent gates on config.isLoggingEnabled() and enqueue on config.isRecordEnabled(); both default to { true }.
      val dispatcher = SimpleLegacyReportDispatcher(
        messageBus,
        FusClientConfig(
          productName = "Test",
          productCode = "TST",
          recorderCode = recorderId,
          recorderVersion = "1",
          regionCode = RegionCode.ALL,
          productVersion = "2025.2",
          baselineVersion = 252,
          anonymizationSalt = null,
          isTest = true,
          reduceInitialMetadataUpdateDelay = false,
        ),
        NoOptionsRemoteConfig(),
        FusComponentProvider.FusJacksonSerializer(),
        NoOpHttpClient(),
        NoOpLoggerFactory(),
        IntellijReportValidator(recorderId),
        eventQueue,
        "test-device",
        false,
        "$recorderId.event.log".lowercase(),
      )

      runBlocking {
        // Default per-group quota is 12000; lower it via the SDK remote-config message so a handful of events trips it.
        // The handler runs synchronously on the Unconfined bus scope; yield() is belt-and-suspenders.
        messageBus.publish(
          REMOTE_CONFIG_OPTIONS_UPDATED,
          mapOf(
            "dataThreshold" to "100000",   // keep the total quota high so only the per-group quota trips
            "groupDataThreshold" to "3",
            "groupAlertThreshold" to "2",
          ),
        )
        yield()

        // Distinct data per event so the SDK merger never collapses them — each one reaches the throttle.
        repeat(EVENT_COUNT) { i -> dispatcher.queueEvent(newEvent(i)) }
        dispatcher.flush()
      }

      val enqueuedIds = eventQueue.enqueued.map { it.event.id }
      assertTrue(
        "Expected a '${EventLogSystemEvents.TOO_MANY_EVENTS}' event after exceeding the group quota, but enqueued ids were $enqueuedIds",
        enqueuedIds.contains(EventLogSystemEvents.TOO_MANY_EVENTS),
      )
    }
    finally {
      scope.cancel()
    }
  }

  private fun newEvent(index: Int): LogEvent = LogEvent(
    session = "session",
    build = "999.999",
    bucket = "1",
    time = System.currentTimeMillis(),
    group = LogEventGroup(THROTTLED_GROUP, "1"),
    recorderVersion = "1",
    event = LogEventAction("test.action", false, hashMapOf("i" to index.toString())),
  )
}

private const val THROTTLED_GROUP = "throttle.test.group"
private const val EVENT_COUNT = 40

private class NoOptionsRemoteConfig : RemoteConfig {
  override fun getSendUrl(): String = ""
  override fun getMetadataUrl(): String = ""
  override fun getDictionaryUrl(): String = ""
  override fun provideOptions(): Map<String, String> = emptyMap()
  override suspend fun update(): Boolean = true
  override suspend fun scheduleUpdate() = Unit
  override fun isUnreachable(): Boolean = false
}

private class NoOpHttpClient : FusHttpClient {
  override fun post(url: String, data: String): HttpResponse = HttpResponse(200, "")
  override fun get(url: String): HttpResponse = HttpResponse(200, "")
  override fun lastModified(url: String): Long = 0L
}

/** Captures everything the dispatcher enqueues (post merge/throttle/validate); [dequeue] is never exercised. */
private class RecordingEventQueue : EventQueue<LogEvent> {
  val enqueued: MutableList<LogEvent> = CopyOnWriteArrayList()

  override lateinit var sendInformationAggregator: SendInformationAggregator
  override val defaultDelay: Duration = 1.hours

  override suspend fun enqueue(item: LogEvent) {
    enqueued.add(item)
  }

  override suspend fun dequeue(action: suspend (List<LogEvent>) -> Boolean): SendResult =
    throw UnsupportedOperationException("send() is not exercised by this test")

  override suspend fun close() = Unit
}
