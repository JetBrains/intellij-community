// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*
import com.intellij.testFramework.PlatformTestCase
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureUsageEventLoggerTest : PlatformTestCase() {

  @Test
  fun testSingleEvent() {
    testLogger(
      { logger -> logger.log(EventLogGroup("group.id", 2), "test-action", false) },
      newEvent("group.id", "test-action", groupVersion = "2")
    )
  }

  @Test
  fun testTwoEvents() {
    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
        logger.log(EventLogGroup("group.id", 2), "second-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2"),
      newEvent("group.id", "second-action", groupVersion = "2")
    )
  }

  @Test
  fun testMergedEvents() {
    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2", count = 2)
    )
  }

  @Test
  fun testTwoMergedEvents() {
    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
        logger.log(EventLogGroup("group.id", 2), "second-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2", count = 2),
      newEvent("group.id", "second-action", groupVersion = "2", count = 1)
    )
  }

  @Test
  fun testNotMergedEvents() {
    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
        logger.log(EventLogGroup("group.id", 2), "second-action", false)
        logger.log(EventLogGroup("group.id", 2), "test-action", false)
      },
      newEvent("group.id", "test-action", groupVersion = "2"),
      newEvent("group.id", "second-action", groupVersion = "2"),
      newEvent("group.id", "test-action", groupVersion = "2")
    )
  }

  @Test
  fun testStateEvent() {
    testLogger(
      { logger -> logger.log(EventLogGroup("group.id", 2), "state", true) },
      newStateEvent("group.id", "state", groupVersion = "2")
    )
  }

  @Test
  fun testEventWithData() {
    val data = HashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("group.id", "dialog-id", groupVersion = "2")
    expected.event.addData("type", "close")
    expected.event.addData("state", 1)

    testLogger({ logger -> logger.log(EventLogGroup("group.id", 2), "dialog-id", data, false) }, expected)
  }

  @Test
  fun testMergeEventWithData() {
    val data = HashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("group.id", "dialog-id", groupVersion = "2")
    expected.event.increment()
    expected.event.addData("type", "close")
    expected.event.addData("state", 1)

    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "dialog-id", data, false)
        logger.log(EventLogGroup("group.id", 2), "dialog-id", data, false)
      }, expected)
  }

  @Test
  fun testStateEventWithData() {
    val data = HashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui", groupVersion = "3")
    expected.event.addData("name", "myOption")
    expected.event.addData("value", true)
    expected.event.addData("default", false)

    testLogger({ logger -> logger.log(EventLogGroup("settings", 3), "ui", data, true) }, expected)
  }

  @Test
  fun testDontMergeStateEventWithData() {
    val data = HashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui", groupVersion = "5")
    expected.event.addData("name", "myOption")
    expected.event.addData("value", true)
    expected.event.addData("default", false)

    testLogger(
      { logger ->
        logger.log(EventLogGroup("settings", 5), "ui", data, true)
        logger.log(EventLogGroup("settings", 5), "ui", data, true)
      },
      expected, expected
    )
  }

  @Test
  fun testDontMergeEventsWithDifferentGroupIds() {
    testLogger(
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
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
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 3), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
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
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action.1", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2"),
      newEvent("group.id", "test.action.1", groupVersion = "2"),
      newEvent("group.id", "test.action", groupVersion = "2")
    )
  }

  @Test
  fun testLoggerWithCustomRecorderVersion() {
    val custom = TestFeatureUsageFileEventLogger("session-id", "999.999", "-1", "99", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, recorderVersion = "99")
    )
  }

  @Test
  fun testLoggerWithCustomSessionId() {
    val custom = TestFeatureUsageFileEventLogger("test.session", "999.999", "-1", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, session = "test.session")
    )
  }

  @Test
  fun testLoggerWithCustomBuildNumber() {
    val custom = TestFeatureUsageFileEventLogger("session-id", "123.456", "-1", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, build = "123.456")
    )
  }

  @Test
  fun testLoggerWithCustomBucket() {
    val custom = TestFeatureUsageFileEventLogger("session-id", "999.999", "215", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(
      custom,
      { logger ->
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
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
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
        logger.log(EventLogGroup("group.id", 2), "test.action", false)
      },
      newEvent("group.id", "test.action", groupVersion = "2", count = 3, session= "my-test.session", build = "123.00.1", bucket = "128", recorderVersion = "29")
    )
  }

  private fun testLogger(callback: (TestFeatureUsageFileEventLogger) -> Unit, vararg expected: LogEvent) {
    val logger = TestFeatureUsageFileEventLogger("session-id", "999.999", "-1", "1", TestFeatureUsageEventWriter())
    testLoggerInternal(logger, callback, *expected)
  }

  private fun testLoggerInternal(logger: TestFeatureUsageFileEventLogger,
                                 callback: (TestFeatureUsageFileEventLogger) -> Unit,
                                 vararg expected: LogEvent) {
    callback(logger)
    logger.dispose()

    val actual = logger.testWriter.logged.mapNotNull { line -> LogEventSerializer.fromString(line) }
    assertEquals(expected.size, actual.size)
    for (i in 0 until expected.size) {
      assertEvent(actual[i], expected[i])
    }
  }

  private fun assertEvent(actual: LogEvent, expected: LogEvent) {
    // Compare events but skip event time
    assertEquals(actual.recorderVersion, expected.recorderVersion)
    assertEquals(actual.session, expected.session)
    assertEquals(actual.bucket, expected.bucket)
    assertEquals(actual.build, expected.build)
    assertEquals(actual.group, expected.group)
    assertEquals(actual.event.id, expected.event.id)

    assertTrue { actual.event.data.containsKey("created") }
    assertTrue { actual.time <= actual.event.data["created"] as Long }

    if (actual.event.isEventGroup()) {
      assertEquals(actual.event.data.size - 2, expected.event.data.size)
      assertTrue { actual.event.data.containsKey("last") }
      assertTrue { actual.time <= actual.event.data["last"] as Long }
    }
    else {
      assertEquals(actual.event.data.size - 1, expected.event.data.size)
    }
    assertEquals(actual.event.state, expected.event.state)
    assertEquals(actual.event.count, expected.event.count)
  }
}

class TestFeatureUsageFileEventLogger(session: String,
                                      build: String,
                                      bucket: String,
                                      recorderVersion: String,
                                      writer: TestFeatureUsageEventWriter) :
  StatisticsFileEventLogger("TEST", session, build, bucket, recorderVersion, writer) {
  val testWriter = writer

  override fun dispose() {
    super.dispose()
    myLogExecutor.awaitTermination(10, TimeUnit.SECONDS)
  }
}

class TestFeatureUsageEventWriter : StatisticsEventLogWriter {
  val logged = ArrayList<String>()

  override fun log(message: String) {
    logged.add(message)
  }

  override fun getActiveFile(): File? = null
  override fun getFiles(): List<File> = emptyList()
  override fun cleanup() = Unit
  override fun rollOver() = Unit
}