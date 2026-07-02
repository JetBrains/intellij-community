// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.TestStatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ObjectDescription
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider
import com.intellij.internal.statistics.StatisticsTestEventFactory.DEFAULT_SESSION_ID
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import com.intellij.internal.statistics.StatisticsTestEventFactory.newStateEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.HttpResponse
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Exercises [StatisticsFileEventLogger] against a real [com.jetbrains.fus.reporting.FusClient] built by
 * [FusComponentProvider.createFusComponents] (with a mock HTTP client, so nothing leaves the process). Merging,
 * throttling, validation and system-field injection all happen inside the SDK dispatcher now, so events are verified
 * as they emerge from the pipeline: the dispatcher republishes each queued event on `RAW_EVENT_TOPIC`, which
 * [FusComponentProvider] forwards to [EventLogListenersManager]. The test subscribes there to capture them.
 */
class FeatureUsageEventLoggerTest : HeavyPlatformTestCase() {

  @Test
  fun testSingleEvent() {
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent("group.id", "test-action", groupVersion = "2"))
  }

  @Test
  fun testTwoEvents() {
    val events = collectViaFusClient(expectedEventCount = 2) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
    }
    assertEquals(2, events.size)
    assertEvent(events[0], newEvent("group.id", "test-action", groupVersion = "2"))
    assertEvent(events[1], newEvent("group.id", "second-action", groupVersion = "2"))
  }

  @Test
  fun testMergedEvents() {
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent("group.id", "test-action", groupVersion = "2", count = 2))
  }

  @Test
  fun testTwoMergedEvents() {
    val events = collectViaFusClient(expectedEventCount = 2) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
    }
    assertEquals(2, events.size)
    assertEvent(events[0], newEvent("group.id", "test-action", groupVersion = "2", count = 2))
    assertEvent(events[1], newEvent("group.id", "second-action", groupVersion = "2", count = 1))
  }

  @Test
  fun testNotMergedEvents() {
    // Only consecutive equal events merge; A, B, A stays three events.
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "test-action", groupVersion = "2"))
    assertEvent(events[1], newEvent("group.id", "second-action", groupVersion = "2"))
    assertEvent(events[2], newEvent("group.id", "test-action", groupVersion = "2"))
  }

  @Test
  fun testStateEvent() {
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "state", true)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newStateEvent("group.id", "state", groupVersion = "2"))
  }

  @Test
  fun testEventWithData() {
    val data = hashMapOf<String, Any>("type" to "close", "state" to 1)
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent("group.id", "dialog-id", groupVersion = "2", data = hashMapOf("type" to "close", "state" to 1)))
  }

  @Test
  fun testMergeEventWithData() {
    val data = hashMapOf<String, Any>("type" to "close", "state" to 1)
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false)
      logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent("group.id", "dialog-id", groupVersion = "2", count = 2,
                                    data = hashMapOf("type" to "close", "state" to 1)))
  }

  @Test
  fun testDontMergeStateEvents() {
    // State events are never merged by the SDK merger.
    val events = collectViaFusClient(expectedEventCount = 2) { logger ->
      logger.logAsync(EventLogGroup("settings", 5), "ui", true)
      logger.logAsync(EventLogGroup("settings", 5), "ui", true)
    }
    assertEquals(2, events.size)
    assertEvent(events[0], newStateEvent("settings", "ui", groupVersion = "5"))
    assertEvent(events[1], newStateEvent("settings", "ui", groupVersion = "5"))
  }

  @Test
  fun testEventsDifferingOnlyInStartTimeAreMerged() {
    // `start_time` is in EventFieldIds.FieldsIgnoredByMerge, which FusComponentProvider forwards to the SDK merger,
    // so successive events differing only in start_time collapse into one counted event.
    val ts = System.currentTimeMillis()
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      val group = EventLogGroup("group.id", 99)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts + 100), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts + 4202), false)
    }
    assertEquals(1, events.size)
    assertEquals("dialog-id", events[0].event.id)
    assertEquals(3, events[0].event.count)
  }

  @Test
  fun testCustomLoggerConfigurationPropagates() {
    // The logger's session/build/bucket/recorderVersion survive the pipeline untouched.
    val events = collectViaFusClient(
      session = "my-test.session",
      build = "123.00.1",
      bucket = "128",
      recorderVersion = "29",
      expectedEventCount = 1,
    ) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent(recorderVersion = "29", groupId = "group.id", groupVersion = "2",
                                    session = "my-test.session", build = "123.00.1", bucket = "128",
                                    eventId = "test.action"))
  }

  @Test
  fun testSystemFieldsInjectedByDispatcher() {
    // preEventWrite (see FusComponentProvider) injects an incrementing system_event_id and `created` on every queued
    // event. This replaces the old fake-writer assertions that read those fields out of the logger directly.
    val events = collectViaFusClient(expectedEventCount = 2) { logger ->
      logger.logAsync(EventLogGroup("group.id.1", 1), "test.action.1", false)
      logger.logAsync(EventLogGroup("group.id.2", 1), "test.action.2", false)
    }
    assertEquals(2, events.size)
    val firstId = events[0].event.data["system_event_id"] as Long
    val secondId = events[1].event.data["system_event_id"] as Long
    assertEquals(firstId + 1, secondId)
    assertTrue { events.all { it.event.data.containsKey("created") } }
    // Tests run headless, so preEventWrite stamps system_headless = true (was testLogHeadlessMode* on the old logger).
    assertTrue { events.all { it.event.data["system_headless"] == true } }
  }

  @Test
  fun testStateEventWithData() {
    val data = hashMapOf<String, Any>("name" to "myOption", "value" to true, "default" to false)
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      logger.logAsync(EventLogGroup("settings", 3), "ui", data, true)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newStateEvent("settings", "ui", groupVersion = "3",
                                         data = hashMapOf("name" to "myOption", "value" to true, "default" to false)))
  }

  @Test
  fun testDontMergeEventsWithDifferentGroupIds() {
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      logger.logAsync(EventLogGroup("group", 2), "test.action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "test.action", groupVersion = "2"))
    assertEvent(events[1], newEvent("group", "test.action", groupVersion = "2"))
    assertEvent(events[2], newEvent("group.id", "test.action", groupVersion = "2"))
  }

  @Test
  fun testDontMergeEventsWithDifferentGroupVersions() {
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      logger.logAsync(EventLogGroup("group.id", 3), "test.action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "test.action", groupVersion = "2"))
    assertEvent(events[1], newEvent("group.id", "test.action", groupVersion = "3"))
    assertEvent(events[2], newEvent("group.id", "test.action", groupVersion = "2"))
  }

  @Test
  fun testDontMergeEventsWithDifferentActions() {
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test.action.1", false)
      logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "test.action", groupVersion = "2"))
    assertEvent(events[1], newEvent("group.id", "test.action.1", groupVersion = "2"))
    assertEvent(events[2], newEvent("group.id", "test.action", groupVersion = "2"))
  }

  @Test
  fun testMergeEventWithIgnoredStartTimeAndSameOtherFields() {
    // start_time is ignored and every other field matches, so the three events merge into one count=3 event
    // (the merged event keeps the first occurrence's data).
    val ts = System.currentTimeMillis()
    val events = collectViaFusClient(expectedEventCount = 1) { logger ->
      val group = EventLogGroup("group.id", 99)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts, "type" to "open"), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts + 1000, "type" to "open"), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to ts + 402, "type" to "open"), false)
    }
    assertEquals(1, events.size)
    assertEvent(events[0], newEvent("group.id", "dialog-id", count = 3, data = hashMapOf("start_time" to ts, "type" to "open")))
  }

  @Test
  fun testDontMergeEventsWithDifferentNonIgnoredField() {
    // start_time is ignored, but `type` differs, so the events do NOT merge.
    val t1 = System.currentTimeMillis()
    val t2 = t1 + 1000
    val t3 = t1 + 402
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      val group = EventLogGroup("group.id", 99)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t1, "type" to "open"), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t2, "type" to "close"), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t3, "type" to "open"), false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t1, "type" to "open")))
    assertEvent(events[1], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t2, "type" to "close")))
    assertEvent(events[2], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t3, "type" to "open")))
  }

  @Test
  fun testDontMergeEventsWithDifferentDataSize() {
    // Differing data-map sizes prevent a merge even when the ignored field matches.
    val t1 = System.currentTimeMillis()
    val t2 = t1 + 1000
    val t3 = t1 + 402
    val events = collectViaFusClient(expectedEventCount = 3) { logger ->
      val group = EventLogGroup("group.id", 99)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t1, "type" to "open"), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t2), false)
      logger.logAsync(group, "dialog-id", hashMapOf<String, Any>("start_time" to t3, "type" to "open"), false)
    }
    assertEquals(3, events.size)
    assertEvent(events[0], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t1, "type" to "open")))
    assertEvent(events[1], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t2)))
    assertEvent(events[2], newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to t3, "type" to "open")))
  }

  @Test
  fun testObjectEvent() {
    /* {
      "intField" : 43
      "obj": {
        "name" : "testName",
        "versions" : ["1", "2"]
      }
    } */

    class TestObjDescription : ObjectDescription() {
      var name by field(EventFields.StringValidatedByCustomRule<CustomValidationRule>("name"))
      var versions by field(EventFields.StringListValidatedByCustomRule<CustomValidationRule>("versions"))
    }

    val group = EventLogGroup("newGroup", 1)
    val event = group.registerEvent("testEvent", EventFields.Int("intField"),
                                    ObjectEventField("obj", TestObjDescription()))

    val intValue = 43
    val testName = "testName"
    val versionsValue = listOf("1", "2")
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      event.log(intValue, ObjectDescription.build(::TestObjDescription) {
        versions = versionsValue
        name = testName
      })
    }
    UsefulTestCase.assertSize(1, events)
    val eventData = events.first().event.data
    UsefulTestCase.assertEquals(intValue, eventData["intField"])
    val objEventData = eventData["obj"] as Map<*, *>
    UsefulTestCase.assertEquals(testName, objEventData["name"])
    val versions = objEventData["versions"] as List<*>
    UsefulTestCase.assertEquals(versionsValue, versions)
  }

  @Test
  fun testObjectVarargEvent() {
    class TestObjDescription : ObjectDescription() {
      var name by field(EventFields.StringValidatedByCustomRule("name", CustomValidationRule::class.java))
      var versions by field(EventFields.StringListValidatedByCustomRule("versions", CustomValidationRule::class.java))
    }

    val group = EventLogGroup("newGroup", 1)
    val intEventField = EventFields.Int("intField")
    val objectEventField = ObjectEventField("obj", TestObjDescription())
    val event = group.registerVarargEvent("testEvent", intEventField, objectEventField)

    val intValue = 43
    val testName = "testName"
    val versionsValue = listOf("1", "2")
    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      event.log(intEventField with intValue, objectEventField with ObjectDescription.build(::TestObjDescription) {
        versions = versionsValue
        name = testName
      })
    }
    UsefulTestCase.assertSize(1, events)
    val eventData = events.first().event.data
    UsefulTestCase.assertEquals(intValue, eventData["intField"])
    val objEventData = eventData["obj"] as Map<*, *>
    UsefulTestCase.assertEquals(testName, objEventData["name"])
    val versions = objEventData["versions"] as List<*>
    UsefulTestCase.assertEquals(versionsValue, versions)
  }

  @Test
  fun testObjectListEventByDescription() {
    class TestObjDescription : ObjectDescription() {
      var name by field(EventFields.StringValidatedByCustomRule("name", CustomValidationRule::class.java))
      var version by field(EventFields.StringValidatedByCustomRule("versions", CustomValidationRule::class.java))
    }

    val group = EventLogGroup("newGroup", 1)
    val objectListField: EventField<List<ObjectEventData>> = ObjectListEventField("objects", TestObjDescription())
    val event = group.registerVarargEvent("testEvent", objectListField)

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      val objList = mutableListOf<ObjectEventData>()
      objList.add(ObjectDescription.build(::TestObjDescription) {
        name = "name1"
        version = "version1"
      })
      objList.add(ObjectDescription.build(::TestObjDescription) {
        name = "name2"
        version = "version2"
      })

      event.log(objectListField with objList)
    }
    UsefulTestCase.assertSize(1, events)
    val eventData = events.first().event.data
    val objectsEventData = eventData["objects"] as List<*>
    UsefulTestCase.assertSize(2, objectsEventData)
  }

  @Test
  fun testObjectInObjectEvent() {
    /* {
      "intField" : 43
      "obj1": {
        "name" : "testName",
        "obj2" : {
          "foo": "fooValue",
          "bar": "barValue",
        }
      }
    } */

    class InnerObjDescription : ObjectDescription() {
      var foo by field(EventFields.StringValidatedByCustomRule("foo", CustomValidationRule::class.java))
      var bar by field(EventFields.StringValidatedByCustomRule("bar", CustomValidationRule::class.java))
    }

    class OuterObjDescription : ObjectDescription() {
      var name by field(EventFields.StringValidatedByCustomRule("name", CustomValidationRule::class.java))
      var obj1 by field(ObjectEventField("obj2", InnerObjDescription()))
    }

    val group = EventLogGroup("newGroup", 1)
    val event = group.registerEvent("testEvent", EventFields.Int("intField"),
                                    ObjectEventField("obj1", OuterObjDescription()))

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      val objectValue = ObjectDescription.build(::OuterObjDescription) {
        name = "testName"
        obj1 = ObjectDescription.build(::InnerObjDescription) {
          bar = "barValue"
          foo = "fooValue"
        }
      }
      event.log(43, objectValue)
    }

    UsefulTestCase.assertSize(1, events)
    val eventData = events.first().event.data
    UsefulTestCase.assertEquals(43, eventData["intField"])
    val obj1EventData = eventData["obj1"] as Map<*, *>
    val obj2EventData = obj1EventData["obj2"] as Map<*, *>
    UsefulTestCase.assertEquals("barValue", obj2EventData["bar"])
  }

  @Test
  fun testEnumInObjectField() {
    /* {
      "obj": {
        "enumField" : "foo"
      }
    } */

    class TestObjDescription : ObjectDescription() {
      var enumField by field(EnumEventField("enumField", TestEnum::class.java) { StringUtil.toLowerCase(it.name) })
    }

    val group = EventLogGroup("newGroup", 1)
    val event = group.registerEvent("testEvent", ObjectEventField("obj", TestObjDescription()))

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      event.log(ObjectDescription.build(::TestObjDescription) {
        enumField = TestEnum.FOO
      })
    }
    UsefulTestCase.assertSize(1, events)
    val objEventData = events.first().event.data["obj"] as Map<*, *>
    UsefulTestCase.assertEquals("foo", objEventData["enumField"])
  }

  enum class TestEnum { FOO, BAR }

  @Test
  fun testMergeStrategyDerivedFromProviderIgnoredFields() {
    val ts = System.currentTimeMillis()
    val first = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to ts))
    val second = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to ts + 100))

    val ignoringProvider = object : StatisticsEventLoggerProvider(
      recorderId = "TEST_MERGE_IGNORED",
      version = 1,
      sendFrequencyMs = DEFAULT_SEND_FREQUENCY_MS,
      maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
      sendLogsOnIdeClose = false,
    ) {
      override fun isRecordEnabled(): Boolean = false
      override fun isSendEnabled(): Boolean = false
      override val mergeIgnoredFields: Set<String> get() = setOf("start_time")
    }
    assertTrue(ignoringProvider.createEventsMergeStrategy().shouldMerge(first, second))

    val defaultProvider = object : StatisticsEventLoggerProvider(
      recorderId = "TEST_MERGE_DEFAULT",
      version = 1,
      sendFrequencyMs = DEFAULT_SEND_FREQUENCY_MS,
      maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
      sendLogsOnIdeClose = false,
    ) {
      override fun isRecordEnabled(): Boolean = false
      override fun isSendEnabled(): Boolean = false
      override val mergeIgnoredFields: Set<String> get() = emptySet()
    }
    assertFalse(defaultProvider.createEventsMergeStrategy().shouldMerge(first, second))
  }

  /**
   * Registers a test recorder, builds a real FusClient (mock HTTP) via [FusComponentProvider.createFusComponents],
   * wires [StatisticsFileEventLogger] to it, runs [action], flushes, and returns the events captured through an
   * [EventLogListenersManager] subscriber (the dispatcher republishes them on RAW_EVENT_TOPIC).
   */
  private fun collectViaFusClient(
    session: String = DEFAULT_SESSION_ID,
    build: String = "999.999",
    bucket: String = "0",
    recorderVersion: String = "1",
    expectedEventCount: Int,
    action: (StatisticsFileEventLogger) -> Unit,
  ): List<LogEvent> {
    // A recording recorder so createFusComponents builds a real (record-enabled) client for it.
    @Suppress("UNCHECKED_CAST")
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(listOf(TestStatisticsEventLoggerProvider(TEST_RECORDER, escapeChars = true)), testRootDisposable, true)
    IntellijSensitiveDataValidator.clearInstances()

    val client = FusComponentProvider.createFusComponents(TEST_RECORDER).fusClient
                 ?: error("FusComponents.fusClient must be built for '$TEST_RECORDER'")
    val received = CopyOnWriteArrayList<LogEvent>()
    val listener = object : StatisticsEventLogListener {
      override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
        received.add(validatedEvent)
      }
    }
    val listenersManager = service<EventLogListenersManager>()
    listenersManager.subscribe(listener, TEST_RECORDER)

    val eventLogDir = Files.createTempDirectory("fus-event-logger-test")
    val logger = StatisticsFileEventLogger(TEST_RECORDER, session, build, bucket, recorderVersion, client, eventLogDir)
    try {
      action(logger)
      // flush() is queued on the logger's single-threaded executor after every logAsync, so it forces the merger to
      // emit and the queue to publish. Delivery to the subscriber is async, so poll for it afterwards.
      logger.flush().get(10, TimeUnit.SECONDS)
      awaitEventCount(received, expectedEventCount)
      return received.toList()
    } finally {
      Disposer.dispose(logger)
      listenersManager.unsubscribe(listener, TEST_RECORDER)
      client.close()
      IntellijSensitiveDataValidator.clearInstances()
      eventLogDir.toFile().deleteRecursively()
    }
  }

  private fun awaitEventCount(received: List<LogEvent>, expected: Int) {
    val deadline = System.currentTimeMillis() + 10_000
    while (received.size < expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
    }
  }

  private fun assertEvent(actual: LogEvent, expected: LogEvent) {
    assertEquals(expected.recorderVersion, actual.recorderVersion)
    assertEquals(expected.session, actual.session)
    assertEquals(expected.bucket, actual.bucket)
    assertEquals(expected.build, actual.build)
    assertEquals(expected.group, actual.group)
    assertEquals(expected.event.id, actual.event.id)
    assertEquals(expected.event.state, actual.event.state)
    assertEquals(expected.event.count, actual.event.count)
    for ((key, value) in expected.event.data) {
      assertEquals("data[$key]", value, actual.event.data[key])
    }
    // Injected by the dispatcher's preEventWrite.
    assertTrue { actual.event.data.containsKey("system_event_id") }
    assertTrue { actual.event.data.containsKey("created") }
  }
}

private const val TEST_RECORDER = "TEST"
