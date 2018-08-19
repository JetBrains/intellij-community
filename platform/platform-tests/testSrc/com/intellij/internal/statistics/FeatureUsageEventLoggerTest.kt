// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*
import com.intellij.util.containers.ContainerUtil
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FeatureUsageEventLoggerTest {

  @Test
  fun testSingleEvent() {
    testLogger(
      { logger -> logger.log("recorder-id", "test-action", false) },
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "test-action", false)
    )
  }

  @Test
  fun testTwoEvents() {
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "second-action", false)
      },
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "test-action", false),
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "second-action", false)
    )
  }

  @Test
  fun testMergedEvents() {
    val action = LogEventAction("test-action", 2)
    val time = System.currentTimeMillis()
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "test-action", false)
      },
      newLogEvent("session-id", "999.999", "-1", time, "recorder-id", "2", action)
    )
  }

  @Test
  fun testTwoMergedEvents() {
    val action = LogEventAction("test-action", 2)
    val time = System.currentTimeMillis()
    testLogger(
      { logger ->
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "test-action", false)
        logger.log("recorder-id", "second-action", false)
      },
      newLogEvent("session-id", "999.999", "-1", time, "recorder-id", "2", action),
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "second-action", false)
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
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "test-action", false),
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "second-action", false),
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "test-action", false)
    )
  }

  @Test
  fun testStateEvent() {
    testLogger(
      { logger -> logger.log("recorder-id", "state", true) },
      newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "state", true)
    )
  }

  @Test
  fun testEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "dialog-id", false)
    expected.event.addData("type", "close")
    expected.event.addData("state", 1)

    testLogger({ logger -> logger.log("recorder-id", "dialog-id", data, false) }, expected)
  }

  @Test
  fun testMergeEventWithData() {
    val data = ContainerUtil.newHashMap<String, Any>()
    data["type"] = "close"
    data["state"] = 1

    val expected = newLogEvent("session-id", "999.999", "-1", "recorder-id", "2", "dialog-id", false)
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

    val expected = newLogEvent("session-id", "999.999", "-1", "settings", "2", "ui", true)
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

    val expected = newLogEvent("session-id", "999.999", "-1", "settings", "2", "ui", true)
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
      assertEventEquals(actual[i], expected[i])
    }
  }

  private fun assertEventEquals(first: LogEvent, second: LogEvent) {
    // Compare events but skip event time
    assertEquals(first.session, second.session)
    assertEquals(first.bucket, second.bucket)
    assertEquals(first.build, second.build)
    assertEquals(first.group, second.group)
    assertEquals(first.event, second.event)
  }
}

class TestFeatureUsageFileEventLogger(session: String, build: String, writer: TestFeatureUsageEventWriter) :
  FeatureUsageFileEventLogger(session, build, "-1", "2", writer) {
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