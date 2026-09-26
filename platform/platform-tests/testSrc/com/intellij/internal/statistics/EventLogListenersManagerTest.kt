// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.ExternalEventLogListenerProviderExtension
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

private const val FIRST_RECORDER = "TEST_RECORDER_ONE"
private const val SECOND_RECORDER = "TEST_RECORDER_TWO"

/**
 * The holder of the providers keeps one provider for each class, so each test recorder needs a class
 * of its own.
 */
private abstract class TestRecorderProvider(recorderId: String) : StatisticsEventLoggerProvider(
  recorderId = recorderId,
  version = 1,
  sendFrequencyMs = -1,
  maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
  sendLogsOnIdeClose = false,
) {
  override fun isRecordEnabled(): Boolean = false

  override fun isSendEnabled(): Boolean = false
}

private class FirstRecorderProvider : TestRecorderProvider(FIRST_RECORDER)

private class SecondRecorderProvider : TestRecorderProvider(SECOND_RECORDER)

private class RecordingListener : StatisticsEventLogListener {
  val events: MutableList<LogEvent> = mutableListOf()

  override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
    events += validatedEvent
  }
}

private class TwoRecorderListenerProvider(
  private val listeners: Map<String, StatisticsEventLogListener>,
) : ExternalEventLogListenerProviderExtension {
  override fun getEventLogListener(recorderId: String): StatisticsEventLogListener? = listeners[recorderId]

  // The subscription must not depend on this flag, because an implementation can compute it from the
  // user consent.
  override fun forceLoggingAlwaysEnabled(): Boolean = false
}

/**
 * Serves [FIRST_RECORDER] only.
 *
 * The manager keeps the listeners of one provider class together, so each provider of a test needs a
 * class of its own.
 */
private abstract class OneRecorderListenerProvider(
  private val listener: StatisticsEventLogListener,
) : ExternalEventLogListenerProviderExtension {
  override fun getEventLogListener(recorderId: String): StatisticsEventLogListener? =
    if (recorderId == FIRST_RECORDER) listener else null

  override fun forceLoggingAlwaysEnabled(): Boolean = false
}

private class StayingListenerProvider(listener: StatisticsEventLogListener) : OneRecorderListenerProvider(listener)

private class LeavingListenerProvider(listener: StatisticsEventLogListener) : OneRecorderListenerProvider(listener)

/** Hides the error that the manager logs for the exception of a listener. */
private object SwallowListenerFailure : LoggedErrorProcessor() {
  override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): MutableSet<Action> =
    if (t is IllegalStateException) Action.NONE else Action.ALL
}

@TestApplication
class EventLogListenersManagerTest {
  @Test
  fun `raw data and jcp payload are withheld from non-jcp listeners outside test mode`() {
    val manager = service<EventLogListenersManager>()
    val recorderId = "JCP_TEST_RECORDER"

    val captured = mutableListOf<Triple<LogEvent, String?, Map<String, Any>?>>()
    val listener = object : StatisticsEventLogListener {
      override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
        captured += Triple(validatedEvent, rawEventId, rawData)
      }
    }

    manager.subscribe(listener, recorderId)
    try {
      val event = logEvent()
      manager.notifySubscribers(
        recorderId, event,
        rawEventId = "raw.id",
        rawData = mapOf(FeatureUsageData.JCP_DATA_KEY to mapOf("loc" to "42"), "foo" to "bar"),
        isFromLocalRecorder = false,
      )

      val (forwardedEvent, rawEventId, rawData) = captured.single()
      assertSame(event, forwardedEvent)
      assertNull(rawEventId, "raw event id must not leak outside test mode")
      assertNull(rawData, "raw data (including the JCP payload) must not leak to non-JCP listeners")
    }
    finally {
      manager.unsubscribe(listener, recorderId)
    }
  }

  @Test
  fun `a provider supplies a listener for each recorder`() {
    val manager = service<EventLogListenersManager>()
    val first = RecordingListener()
    val second = RecordingListener()
    val mask = Disposer.newDisposable()
    try {
      maskExtensions(mask, first, second)

      val firstEvent = logEvent("first.event")
      val secondEvent = logEvent("second.event")
      manager.notifySubscribers(FIRST_RECORDER, firstEvent, null, null, isFromLocalRecorder = false)
      manager.notifySubscribers(SECOND_RECORDER, secondEvent, null, null, isFromLocalRecorder = false)

      assertEquals(listOf(firstEvent), first.events, "the listener of the first recorder must get its event")
      assertEquals(listOf(secondEvent), second.events, "the listener of the second recorder must get its event")
    }
    finally {
      Disposer.dispose(mask)
    }
  }

  @Test
  fun `the manager removes the listener of each recorder with the extension`() {
    val manager = service<EventLogListenersManager>()
    val first = RecordingListener()
    val second = RecordingListener()

    val mask = Disposer.newDisposable()
    maskExtensions(mask, first, second)
    Disposer.dispose(mask)

    manager.notifySubscribers(FIRST_RECORDER, logEvent("first.event"), null, null, isFromLocalRecorder = false)
    manager.notifySubscribers(SECOND_RECORDER, logEvent("second.event"), null, null, isFromLocalRecorder = false)

    assertEquals(emptyList<LogEvent>(), first.events, "the listener of the first recorder must get no event")
    assertEquals(emptyList<LogEvent>(), second.events, "the listener of the second recorder must get no event")
  }

  @Test
  fun `the manager keeps the listener of one extension when it removes another extension`() {
    val manager = service<EventLogListenersManager>()
    val staying = RecordingListener()
    val leaving = RecordingListener()

    val mask = Disposer.newDisposable()
    val stayingExtension = Disposer.newDisposable()
    val leavingExtension = Disposer.newDisposable()
    try {
      maskRecorders(mask)
      // Register each extension on its own, because one mask of the point cannot remove one
      // extension.
      val point = ExternalEventLogListenerProviderExtension.EP_NAME.point
      point.registerExtension(StayingListenerProvider(staying), stayingExtension)
      point.registerExtension(LeavingListenerProvider(leaving), leavingExtension)

      Disposer.dispose(leavingExtension)

      val event = logEvent("first.event")
      manager.notifySubscribers(FIRST_RECORDER, event, null, null, isFromLocalRecorder = false)

      assertEquals(listOf(event), staying.events, "the listener of the other extension must still get the event")
      assertEquals(emptyList<LogEvent>(), leaving.events, "the listener of the removed extension must get no event")
    }
    finally {
      Disposer.dispose(stayingExtension)
      Disposer.dispose(mask)
    }
  }

  @Test
  fun `the manager subscribes a listener provider that arrives after the start`() {
    val manager = service<EventLogListenersManager>()
    val listener = RecordingListener()

    val mask = Disposer.newDisposable()
    val extension = Disposer.newDisposable()
    try {
      maskRecorders(mask)
      // The manager already exists, so this extension arrives after the start of the application.
      ExternalEventLogListenerProviderExtension.EP_NAME.point.registerExtension(StayingListenerProvider(listener), extension)

      val event = logEvent("first.event")
      manager.notifySubscribers(FIRST_RECORDER, event, null, null, isFromLocalRecorder = false)

      assertEquals(listOf(event), listener.events, "a late extension must also get its listener subscribed")
    }
    finally {
      Disposer.dispose(extension)
      Disposer.dispose(mask)
    }
  }

  @Test
  fun `a recorder gets no listener when the provider returns null for it`() {
    val manager = service<EventLogListenersManager>()
    val listener = RecordingListener()

    val mask = Disposer.newDisposable()
    val extension = Disposer.newDisposable()
    try {
      maskRecorders(mask)
      // The provider serves the first recorder only, and it returns null for the second recorder.
      ExternalEventLogListenerProviderExtension.EP_NAME.point.registerExtension(StayingListenerProvider(listener), extension)

      // Show that the subscription works, so the assertion below cannot pass for another reason.
      val firstEvent = logEvent("first.event")
      manager.notifySubscribers(FIRST_RECORDER, firstEvent, null, null, isFromLocalRecorder = false)
      assertEquals(listOf(firstEvent), listener.events, "the listener must get the event of the first recorder")

      manager.notifySubscribers(SECOND_RECORDER, logEvent("second.event"), null, null, isFromLocalRecorder = false)

      assertEquals(listOf(firstEvent), listener.events, "the listener must get no event of the second recorder")
    }
    finally {
      Disposer.dispose(extension)
      Disposer.dispose(mask)
    }
  }

  @Test
  fun `an event of the local recorder does not reach a listener that the manager does not allow`() {
    val manager = service<EventLogListenersManager>()
    val first = RecordingListener()
    val second = RecordingListener()

    val mask = Disposer.newDisposable()
    try {
      maskExtensions(mask, first, second)

      // Show that the subscription works, so the assertion below cannot pass for another reason.
      val event = logEvent("first.event")
      manager.notifySubscribers(FIRST_RECORDER, event, null, null, isFromLocalRecorder = false)
      assertEquals(listOf(event), first.events, "the listener must get the event of the normal recorder")

      // The manager allows one hardcoded listener class for the local recorder, and this test uses
      // another class. The test does not cover the allowed class, which lives outside this project.
      manager.notifySubscribers(FIRST_RECORDER, logEvent("local.event"), null, null, isFromLocalRecorder = true)

      assertEquals(listOf(event), first.events, "a local event must not reach a listener that is not allowed")
    }
    finally {
      Disposer.dispose(mask)
    }
  }

  @Test
  fun `an exception of one listener does not stop another listener`() {
    val manager = service<EventLogListenersManager>()
    val recorderId = "TEST_RECORDER_WITH_FAILURE"
    val failures = mutableListOf<LogEvent>()
    val failing = object : StatisticsEventLogListener {
      override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
        failures += validatedEvent
        throw IllegalStateException("the listener of the test fails on purpose")
      }
    }
    val working = RecordingListener()

    // The manager keeps the order of the subscriptions, so the failing listener comes first.
    manager.subscribe(failing, recorderId)
    manager.subscribe(working, recorderId)
    try {
      val event = logEvent()
      LoggedErrorProcessor.executeWith<Throwable>(SwallowListenerFailure) {
        manager.notifySubscribers(recorderId, event, null, null, isFromLocalRecorder = false)
      }

      assertEquals(listOf(event), working.events, "the second listener must get the event")
      assertEquals(listOf(event), failures, "the first listener must get the event and fail on it")
    }
    finally {
      manager.unsubscribe(failing, recorderId)
      manager.unsubscribe(working, recorderId)
    }
  }

  /** Puts one logger provider for each test recorder, and one listener provider that serves both. */
  private fun maskExtensions(mask: Disposable, first: StatisticsEventLogListener, second: StatisticsEventLogListener) {
    maskRecorders(mask)
    ExtensionTestUtil.maskExtensions(
      ExternalEventLogListenerProviderExtension.EP_NAME,
      listOf(TwoRecorderListenerProvider(mapOf(FIRST_RECORDER to first, SECOND_RECORDER to second))),
      mask,
    )
  }

  /** Puts one logger provider for each test recorder. */
  private fun maskRecorders(mask: Disposable) {
    ExtensionTestUtil.maskExtensions(
      StatisticsEventLoggerProvider.EP_NAME,
      listOf(FirstRecorderProvider(), SecondRecorderProvider()),
      mask,
    )
  }

  private fun logEvent(eventId: String = "event.id"): LogEvent =
    LogEvent(
      session = "session", build = "build", bucket = "0",
      time = 0L, group = LogEventGroup("group.id", "1"),
      recorderVersion = "1", event = LogEventAction(eventId, false, HashMap()),
    )
}
