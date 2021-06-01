// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.execution.process.ProcessOutputType
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventAction
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*
import com.intellij.internal.statistic.toolwindow.StatisticsEventLogFilter.Companion.LOG_PATTERN
import com.intellij.internal.statistic.toolwindow.StatisticsEventLogMessageBuilder
import com.intellij.internal.statistic.toolwindow.StatisticsEventLogToolWindow
import com.intellij.internal.statistic.toolwindow.StatisticsLogFilterModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.DateFormatUtil
import org.junit.Test

class StatisticsEventLogToolWindowTest : BasePlatformTestCase() {
  private val eventId = "third.party"
  private val eventTime = 1564643114456
  private val eventGroup = "toolwindow"
  private val groupVersion = "21"
  private val formattedEventTime = DateFormatUtil.formatTimeWithSeconds(eventTime)

  @Test
  fun testShortenProjectId() {
    val action = LogEventAction(eventId)
    val data = hashMapOf(
      "project" to "5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea",
      "plugin_type" to "PLATFORM"
    )
    for ((key, value) in data) {
      action.addData(key, value)
    }

    doTestCountCollector("{\"plugin_type\":\"PLATFORM\", \"project\":\"5410c65e...ea\"}", action, data)
  }

  @Test
  fun testNotShortenProjectId() {
    val action = LogEventAction(eventId)
    val projectId = "12345"
    action.addData("project", projectId)

    doTestCountCollector("{\"project\":\"$projectId\"}", action, hashMapOf("project" to projectId))
  }

  @Test
  fun testFilterSystemFields() {
    val action = LogEventAction(eventId)
    val data = hashMapOf(
      "last" to "1564643442610",
      "created" to "1564643442610"
    )
    for ((key, value) in data) {
      action.addData(key, value)
    }

    doTestCountCollector("{}", action, data)
  }

  @Test
  fun testLogIncorrectEventIdAsError() {
    val incorrectEventId = INCORRECT_RULE.description
    val action = LogEventAction(incorrectEventId)

    val filterModel = StatisticsLogFilterModel()
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$incorrectEventId[$eventId]\" {}", logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  @Test
  fun testLogIncorrectEventDataAsError() {
    val action = LogEventAction(eventId)
    action.addData("test", INCORRECT_RULE.description)
    action.addData("project", UNDEFINED_RULE.description)

    val filterModel = StatisticsLogFilterModel()
    val rawData = hashMapOf(
      "test" to "foo",
      "project" to "5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea"
    )
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, rawData)
    val expectedLine = buildExpectedLine(
      "{\"test\":\"validation.incorrect_rule[foo]\", \"project\":\"validation.undefined_rule[5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea]\"}")
    assertEquals(expectedLine, logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  @Test
  fun testLogIncorrectEventDataWithoutRawData() {
    val action = LogEventAction(eventId)
    action.addData("test", INCORRECT_RULE.description)
    action.addData("project", UNDEFINED_RULE.description)
    action.addData("map", hashMapOf("foo" to "bar"))
    action.addData("list", listOf("foo"))

    val filterModel = StatisticsLogFilterModel()
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), null, null)
    val expectedLine = buildExpectedLine(
      "{\"test\":\"validation.incorrect_rule\", \"project\":\"validation.undefined_rule\", \"list\":[\"foo\"], \"map\":{\"foo\":\"bar\"}}")
    assertEquals(expectedLine, logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  @Test
  fun testAllValidationTypesUsed() {
    val correctValidationTypes = setOf(ACCEPTED, THIRD_PARTY)
    for (resultType in ValidationResultType.values()) {
      assertTrue("Don't forget to change toolWindow logic in case of a new value in ValidationResult",
                 StatisticsEventLogToolWindow.rejectedValidationTypes.contains(resultType) || correctValidationTypes.contains(resultType))
    }
  }

  @Test
  fun testHandleCollectionsInEventData() {
    val action = LogEventAction(eventId)
    action.addData("dataKey", listOf("1", "2", "3"))

    doTestCountCollector("{\"dataKey\":[\"1\",\"2\",\"3\"]}", action, hashMapOf("dataKey" to listOf("1", "2", "3")))
  }

  @Test
  fun testLogCountCollectors() {
    val count = 2
    val action = LogEventAction(eventId, false, count)

    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" (count=${count}) {}", actual)
  }

  @Test
  fun testLogLineWithCountMatchesRegexpPattern() {
    val action = LogEventAction(eventId, false, 2)

    val logLineWithCount = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    val matcher = LOG_PATTERN.matcher(logLineWithCount)
    assertTrue(matcher.find())
    assertEquals(eventGroup, matcher.group("groupId"))
    assertEquals(eventId, matcher.group("event"))
    assertEquals("{}", matcher.group("eventData"))
  }

  @Test
  fun testLogLineMatchesRegexpPattern() {
    val action = LogEventAction(eventId, true)
    action.addData("plugin_type", "PLATFORM")

    val logLineWithCount = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    val matcher = LOG_PATTERN.matcher(logLineWithCount)
    assertTrue(matcher.find())
    assertEquals(eventGroup, matcher.group("groupId"))
    assertEquals(eventId, matcher.group("event"))
    assertEquals("{\"plugin_type\":\"PLATFORM\"}", matcher.group("eventData"))
  }

  private fun doTestCountCollector(expectedEventDataPart: String, action: LogEventAction, rawData: Map<String, Any> = emptyMap()) {
    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, rawData)
    assertEquals(buildExpectedLine(expectedEventDataPart), actual)
  }

  private fun buildExpectedLine(expectedEventDataPart: String) =
    "$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" $expectedEventDataPart"

  private fun buildLogEvent(action: LogEventAction, groupId: String = eventGroup) = LogEvent("2e5b2e32e061", "193.1801", "176", eventTime,
                                                                                             groupId, groupVersion, "32", action)
}