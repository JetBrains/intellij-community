// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistics.StatisticsTestEventFactory.DEFAULT_SESSION_ID
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import com.intellij.internal.statistics.StatisticsTestEventFactory.newStateEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class FeatureUsageEventLoggerTest : HeavyPlatformTestCase() {

  @Test
  fun testSingleEvent() {
    testLogger(
      { logger -> logger.logAsync(EventLogGroup("group.id", 2), "test-action", false) },
      newEvent("group.id", "test-action", groupVersion = "2")
    )
  }

  @Test
  fun testTwoEvents() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2"),
      newEvent("group.id", "second-action", groupVersion = "2")
    )
  }

  @Test
  fun testMergedEvents() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2", count = 2)
    )
  }

  @Test
  fun testTwoMergedEvents() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2", count = 2),
      newEvent("group.id", "second-action", groupVersion = "2", count = 1)
    )
  }

  @Test
  fun testNotMergedEvents() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "second-action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2"),
      newEvent("group.id", "second-action", groupVersion = "2"),
      newEvent("group.id", "test-action", groupVersion = "2")
    )
  }

  @Test
  fun testStateEvent() {
    testLogger(
      { logger -> logger.logAsync(EventLogGroup("group.id", 2), "state", true) },
      newStateEvent("group.id", "state", groupVersion = "2")
    )
  }

  @Test
  fun testEventWithData() {
    val data = HashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("group.id", "dialog-id", groupVersion = "2",
      data = hashMapOf("type" to "close", "state" to 1))

    testLogger({ logger -> logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false) }, expected)
  }

  @Test
  fun testMergeEventWithData() {
    val data = HashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("group.id", "dialog-id", groupVersion = "2", data = hashMapOf("type" to "close", "state" to 1))
    expected.event.increment()

    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false)
        logger.logAsync(EventLogGroup("group.id", 2), "dialog-id", data, false)
      }, expected)
  }

  @Test
  fun testMergeEventWithoutFilteredData() {
    val ts = System.currentTimeMillis()
    val first = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to ts))
    val second = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to 100 + ts))
    val third = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to 4202 + ts))

    testLogger(
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to ts), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 100 + ts), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 4202 + ts), false)
      }, first, second, third)
  }

  @Test
  fun testMergeEventWithMultipleNotFilteredData() {
    val ts = System.currentTimeMillis()
    val first = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to ts, "type" to "open"))
    val second = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to 1000 + ts, "type" to "open"))
    val third = newEvent("group.id", "dialog-id", count = 1, data = hashMapOf("start_time" to 402 + ts, "type" to "open"))

    testLogger(
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to ts, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 1000 + ts, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 402 + ts, "type" to "open"), false)
      }, first, second, third)
  }


  @Test
  fun testMergeEventWithFilteredData() {
    val ts = System.currentTimeMillis()
    val expected = newEvent("group.id", "dialog-id", count = 3, data = hashMapOf("start_time" to ts))

    val loggerWithMergeStrategy = TestFeatureUsageFileEventLogger(mergeStrategy = FilteredEventMergeStrategy(hashSetOf("start_time")))
    testLoggerInternal(
      loggerWithMergeStrategy,
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to ts), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 100 + ts), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 4202 + ts), false)
      }, expected)
  }

  @Test
  fun testMergeEventWithFilteredDataAndOtherFields() {
    val ts = System.currentTimeMillis()
    val expected = newEvent("group.id", "dialog-id", count = 3, data = hashMapOf("start_time" to ts, "type" to "open"))

    val loggerWithMergeStrategy = TestFeatureUsageFileEventLogger(mergeStrategy = FilteredEventMergeStrategy(hashSetOf("start_time")))
    testLoggerInternal(
      loggerWithMergeStrategy,
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to ts, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 1000 + ts, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to 402 + ts, "type" to "open"), false)
      }, expected)
  }

  @Test
  fun testMergeEventWithFilteredDataAndOtherDifferentFields() {
    val startTimeMs1 = System.currentTimeMillis()
    val startTimeMs2 = 1000 + System.currentTimeMillis()
    val startTimeMs3 = 402 + System.currentTimeMillis()

    val first = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs1, "type" to "open"))
    val second = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs2, "type" to "close"))
    val third = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs3, "type" to "open"))

    val loggerWithMergeStrategy = TestFeatureUsageFileEventLogger(mergeStrategy = FilteredEventMergeStrategy(hashSetOf("start_time")))
    testLoggerInternal(
      loggerWithMergeStrategy,
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs1, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs2, "type" to "close"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs3, "type" to "open"), false)
      }, first, second, third)
  }

  @Test
  fun testMergeEventWithFilteredDataAndOtherDifferentFieldsSize() {
    val startTimeMs1 = System.currentTimeMillis()
    val startTimeMs2 = 1000 + System.currentTimeMillis()
    val startTimeMs3 = 402 + System.currentTimeMillis()

    val first = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs1, "type" to "open"))
    val second = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs2))
    val third = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs3, "type" to "open"))

    val loggerWithMergeStrategy = TestFeatureUsageFileEventLogger(mergeStrategy = FilteredEventMergeStrategy(hashSetOf("start_time")))
    testLoggerInternal(
      loggerWithMergeStrategy,
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs1, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs2), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs3, "type" to "open"), false)
      }, first, second, third)
  }

  @Test
  fun testMergeEventWithFilteredDataAndOtherDifferentFieldsSize2() {
    val startTimeMs1 = System.currentTimeMillis()
    val startTimeMs2 = 1000 + System.currentTimeMillis()
    val startTimeMs3 = 402 + System.currentTimeMillis()

    val first = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs1, "type" to "open"))
    val second = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs2, "value" to 13, "result" to "succeed"))
    val third = newEvent("group.id", "dialog-id", data = hashMapOf("start_time" to startTimeMs3, "type" to "open"))

    val loggerWithMergeStrategy = TestFeatureUsageFileEventLogger(mergeStrategy = FilteredEventMergeStrategy(hashSetOf("start_time")))
    testLoggerInternal(
      loggerWithMergeStrategy,
      { logger ->
        val group = EventLogGroup("group.id", 99)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs1, "type" to "open"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs2, "value" to 13, "result" to "succeed"), false)
        logger.logAsync(group, "dialog-id", hashMapOf("start_time" to startTimeMs3, "type" to "open"), false)
      }, first, second, third)
  }

  @Test
  fun testStateEventWithData() {
    val data = HashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui", groupVersion = "3",
      data = hashMapOf("name" to "myOption", "value" to true, "default" to false))

    testLogger({ logger -> logger.logAsync(EventLogGroup("settings", 3), "ui", data, true) }, expected)
  }

  @Test
  fun testDontMergeStateEventWithData() {
    val data = HashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui", groupVersion = "5",
      data = hashMapOf("name" to "myOption", "value" to true, "default" to false))

    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("settings", 5), "ui", data, true)
        logger.logAsync(EventLogGroup("settings", 5), "ui", data, true)
      },
      expected, expected
    )
  }

  @Test
  fun testDontMergeEventsWithDifferentGroupIds() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2"),
      newEvent("group", "test.action", groupVersion = "2"),
      newEvent("group.id", "test.action", groupVersion = "2")
    )
  }

  @Test
  fun testDontMergeEventsWithDifferentGroupVersions() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 3), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2"),
      newEvent("group.id", "test.action", groupVersion = "3"),
      newEvent("group.id", "test.action", groupVersion = "2")
    )
  }

  @Test
  fun testDontMergeEventsWithDifferentActions() {
    testLogger(
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action.1", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2"),
      newEvent("group.id", "test.action.1", groupVersion = "2"),
      newEvent("group.id", "test.action", groupVersion = "2")
    )
  }

  @Test
  fun testLoggerWithCustomRecorderVersion() {
    val custom = TestFeatureUsageFileEventLogger(DEFAULT_SESSION_ID, "999.999", "0", "99", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, recorderVersion = "99")
    )
  }

  @Test
  fun testLoggerWithCustomSessionId() {
    val custom = TestFeatureUsageFileEventLogger("test.session", "999.999", "0", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, session = "test.session")
    )
  }

  @Test
  fun testLoggerWithCustomBuildNumber() {
    val custom = TestFeatureUsageFileEventLogger(DEFAULT_SESSION_ID, "123.456", "0", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, build = "123.456")
    )
  }

  @Test
  fun testLoggerWithCustomBucket() {
    val custom = TestFeatureUsageFileEventLogger(DEFAULT_SESSION_ID, "999.999", "215", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, bucket = "215")
    )
  }

  @Test
  fun testCustomLogger() {
    val custom = TestFeatureUsageFileEventLogger("my-test.session", "123.00.1", "128", "29", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
        logger.logAsync(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent(
        recorderVersion = "29", groupId = "group.id", groupVersion = "2",
        session = "my-test.session", build = "123.00.1", bucket = "128",
        eventId = "test.action", count = 3
      )
    )
  }

  @Test
  fun testLogSystemEventId() {
    val logger = TestFeatureUsageFileEventLogger(DEFAULT_SESSION_ID, "999.999", "0", "1",
                                                 TestFeatureUsageEventWriter(), TestSystemEventIdProvider(42L))
    logger.logAsync(EventLogGroup("group.id.1", 1), "test.action.1", false)
    logger.logAsync(EventLogGroup("group.id.2", 1), "test.action.2", false)
    logger.dispose()
    val logged = logger.testWriter.logged
    UsefulTestCase.assertSize(2, logged)
    assertEquals(logged[0].event.data["system_event_id"], 42.toLong())
    assertEquals(logged[1].event.data["system_event_id"], 43.toLong())
  }

  @Test
  fun testLogHeadlessModeEnabled() {
    doTestHeadlessMode(true) {
      assertEquals(it.event.data["system_headless"], true)
    }
  }

  @Test
  fun testLogHeadlessModeDisabled() {
    doTestHeadlessMode(false) {
      assertFalse(it.event.data.containsKey("system_headless"))
    }
  }

  private fun doTestHeadlessMode(headless: Boolean, assertion: (LogEvent) -> Unit) {
    val logger = TestFeatureUsageFileEventLogger(headless = headless)
    logger.logAsync(EventLogGroup("group.id", 1), "test.action", false)
    logger.dispose()

    val loggedEvents = logger.testWriter.logged
    UsefulTestCase.assertSize(1, loggedEvents)
    assertion.invoke(loggedEvents[0])
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
      var name by field(EventFields.StringValidatedByCustomRule("name", "name_rule"))
      var versions by field(EventFields.StringListValidatedByCustomRule("versions", "version_rule"))
    }

    val group = EventLogGroup("newGroup", 1)
    val event = group.registerEvent("testEvent", EventFields.Int("intField"),
                                    ObjectEventField("obj", TestObjDescription()))

    val intValue = 43
    val testName = "testName"
    val versionsValue = listOf("1", "2")
    val events = FUCollectorTestCase.collectLogEvents {
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
      var name by field(EventFields.StringValidatedByCustomRule("name", "name_rule"))
      var versions by field(EventFields.StringListValidatedByCustomRule("versions", "version_rule"))
    }

    val group = EventLogGroup("newGroup", 1)
    val intEventField = EventFields.Int("intField")
    val objectEventField = ObjectEventField("obj", TestObjDescription())
    val event = group.registerVarargEvent("testEvent", intEventField, objectEventField)

    val intValue = 43
    val testName = "testName"
    val versionsValue = listOf("1", "2")
    val events = FUCollectorTestCase.collectLogEvents {
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
      var name by field(EventFields.StringValidatedByCustomRule("name", "name_rule"))
      var version by field(EventFields.StringValidatedByCustomRule("versions", "version_rule"))
    }

    val group = EventLogGroup("newGroup", 1)
    val objectListField: EventField<List<ObjectEventData>> = ObjectListEventField("objects", TestObjDescription())
    val event = group.registerVarargEvent("testEvent", objectListField)

    val events = FUCollectorTestCase.collectLogEvents {
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
      var foo by field(EventFields.StringValidatedByCustomRule("foo", "foo_rule"))
      var bar by field(EventFields.StringValidatedByCustomRule("bar", "bar_rule"))
    }

    class OuterObjDescription : ObjectDescription() {
      var name by field(EventFields.StringValidatedByCustomRule("name", "name_rule"))
      var obj1 by field(ObjectEventField("obj2", InnerObjDescription()))
    }

    val group = EventLogGroup("newGroup", 1)
    val event = group.registerEvent("testEvent", EventFields.Int("intField"),
                                    ObjectEventField("obj1", OuterObjDescription()))

    val events = FUCollectorTestCase.collectLogEvents {
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

    val events = FUCollectorTestCase.collectLogEvents {
      event.log(ObjectDescription.build(::TestObjDescription) {
        enumField = TestEnum.FOO
      })
    }
    UsefulTestCase.assertSize(1, events)
    val objEventData = events.first().event.data["obj"] as Map<*, *>
    UsefulTestCase.assertEquals("foo", objEventData["enumField"])
  }

  enum class TestEnum { FOO, BAR }

  private fun testLogger(callback: (TestFeatureUsageFileEventLogger) -> Unit, vararg expected: LogEvent) {
    val logger = TestFeatureUsageFileEventLogger(DEFAULT_SESSION_ID, "999.999", "0", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(logger, callback, *expected)
  }

  private fun testLoggerInternal(logger: TestFeatureUsageFileEventLogger,
                                 callback: (TestFeatureUsageFileEventLogger) -> Unit,
                                 vararg expected: LogEvent) {
    callback(logger)
    logger.dispose()

    val actual = logger.testWriter.logged
    assertEquals(expected.size, actual.size)
    for (i in 0 until expected.size) {
      assertEvent(actual[i], expected[i])
    }
  }

  private fun assertEvent(actual: LogEvent, expected: LogEvent) {
    // Compare events but skip event time
    assertEquals(expected.recorderVersion, actual.recorderVersion)
    assertEquals(expected.session, actual.session)
    assertEquals(expected.bucket, actual.bucket)
    assertEquals(expected.build, actual.build)
    assertEquals(expected.group, actual.group)
    assertEquals(expected.event.id, actual.event.id)

    assertTrue { actual.event.data.containsKey("created") }
    assertTrue { actual.event.data.containsKey("system_event_id") }
    assertTrue { actual.time <= actual.event.data["created"] as Long }

    if (actual.event.isEventGroup()) {
      assertEquals(expected.event.data.size, actual.event.data.size - 3)
      assertTrue { actual.event.data.containsKey("last") }
      assertTrue { actual.time <= actual.event.data["last"] as Long }
    }
    else {
      assertEquals(expected.event.data.size, actual.event.data.size - 2)
    }
    assertEquals(expected.event.state, actual.event.state)
    assertEquals(expected.event.count, actual.event.count)
  }
}

private const val TEST_RECORDER = "TEST"

class TestFeatureUsageFileEventLogger(session: String = DEFAULT_SESSION_ID,
                                      build: String = "999.999",
                                      bucket: String = "0",
                                      recorderVersion: String = "1",
                                      writer: TestFeatureUsageEventWriter = TestFeatureUsageEventWriter(),
                                      systemEventIdProvider: StatisticsSystemEventIdProvider = TestSystemEventIdProvider(0),
                                      mergeStrategy: StatisticsEventMergeStrategy = FilteredEventMergeStrategy(emptySet()),
                                      headless: Boolean = false) :
  StatisticsFileEventLogger(TEST_RECORDER, session, headless, build, bucket, recorderVersion, writer, systemEventIdProvider, mergeStrategy) {
  val testWriter = writer

  override fun dispose() {
    super.dispose()
    logExecutor.awaitTermination(10, TimeUnit.SECONDS)
  }
}

class TestFeatureUsageEventWriter : StatisticsEventLogWriter {
  val logged = ArrayList<LogEvent>()

  override fun log(logEvent: LogEvent) {
    logged.add(logEvent)
  }

  override fun getActiveFile(): EventLogFile? = null
  override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider
  override fun cleanup() = Unit
  override fun rollOver() = Unit
  override fun dispose() = Unit
}

class TestSystemEventIdProvider(var value: Long) : StatisticsSystemEventIdProvider {
  override fun getSystemEventId(recorderId: String): Long {
    return value
  }

  override fun setSystemEventId(recorderId: String, eventId: Long) {
    value = eventId
  }
}