// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*
import com.intellij.util.containers.ContainerUtil
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureUsageEventLoggerTest {

  @Test
  fun testSingleEvent() {
    testLogger(
      { logger -> logger.log("recorder-id", "test-action", false) },
      newEvent("recorder-id", "test-action")
    )
  }

  @Test
  fun testTwoEvents() {
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "second-action", false)
      },
      newEvent("recorder-id", "test-action"),
      newEvent("recorder-id", "second-action")
    )
  }

  @Test
  fun testMergedEvents() {
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "test-action", false)
      },
      newEvent("recorder-id", "test-action", count = 2)
    )
  }

  @Test
  fun testTwoMergedEvents() {
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "second-action", false)
      },
      newEvent("recorder-id", "test-action", count = 2),
      newEvent("recorder-id", "second-action", count = 1)
    )
  }

  @Test
  fun testNotMergedEvents() {
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "second-action", false)
        logger.log("recorder-id", "test-action", false)
      },
      newEvent("recorder-id", "test-action"),
      newEvent("recorder-id", "second-action"),
      newEvent("recorder-id", "test-action")
    )
  }

  @Test
  fun testStateEvent() {
    testLogger(
      { logger -> logger.log("recorder-id", "state", true) },
      newStateEvent("recorder-id", "state")
    )
  }

  @Test
  fun testEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("recorder-id", "dialog-id")
    expected.event.addData("type", "close")
    expected.event.addData("state", 1)

    testLogger({ logger -> logger.log("recorder-id", "dialog-id", data, false) }, expected)
  }

  @Test
  fun testMergeEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newEvent("recorder-id", "dialog-id")
    expected.event.increment()
    expected.event.addData("type", "close")
    expected.event.addData("state", 1)

    testLogger(
      { logger ->
        logger.log("recorder-id", "dialog-id", data, false)
        logger.log("recorder-id", "dialog-id", data, false)
      }, expected)
  }

  @Test
  fun testStateEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui")
    expected.event.addData("name", "myOption")
    expected.event.addData("value", true)
    expected.event.addData("default", false)

    testLogger({ logger -> logger.log("settings", "ui", data, true) }, expected)
  }

  @Test
  fun testDontMergeStateEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["name"] = "myOption"
    data["value"] = true
    data["default"] = false

    val expected = newStateEvent("settings", "ui")
    expected.event.addData("name", "myOption")
    expected.event.addData("value", true)
    expected.event.addData("default", false)

    testLogger(
      { logger ->
        logger.log("settings", "ui", data, true)
        logger.log("settings", "ui", data, true)
      },
      expected, expected
    )
  }

  private fun testLogger(callback: (TestFeatureUsageFileEventLogger) -> Unit, vararg expected: LogEvent) {
    val logger = TestFeatureUsageFileEventLogger("session-id", "999.999", TestFeatureUsageEventWriter())
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
    if (actual.event is LogEventAction || expected.event is LogEventAction) {
      assertEquals((actual.event as LogEventAction).count, (expected.event as LogEventAction).count)
    }
  }
}

class TestFeatureUsageFileEventLogger(session: String, build: String, writer: TestFeatureUsageEventWriter) :
  FeatureUsageFileEventLogger(session, build, "-1", "1", writer) {
  val testWriter = writer

  override fun dispose() {
    super.dispose()
    myLogExecutor.awaitTermination(10, TimeUnit.SECONDS)
  }
}

class TestFeatureUsageEventWriter : FeatureUsageEventWriter {
  val logged = ArrayList<String>()

  override fun log(message: String) {
    logged.add(message)
  }

  override fun getFiles(): List<File> {
    return emptyList()
  }
}