// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    action.addData("project", "5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea")
    action.addData("plugin_type", "PLATFORM")

    doTestCountCollector("{\"plugin_type\":\"PLATFORM\", \"project\":\"5410c65e...ea\"}", action)
  }

  @Test
  fun testNotShortenProjectId() {
    val action = LogEventAction(eventId)
    val projectId = "12345"
    action.addData("project", projectId)

    doTestCountCollector("{\"project\":\"$projectId\"}", action)
  }

  @Test
  fun testFilterSystemFields() {
    val action = LogEventAction(eventId)
    action.addData("last", "1564643442610")
    action.addData("created", "1564643442610")

    doTestCountCollector("{}", action)
  }

  @Test
  fun testLogIncorrectEventIdAsError() {
    val incorrectEventId = INCORRECT_RULE.description
    val action = LogEventAction(incorrectEventId)

    val filterModel = StatisticsLogFilterModel()
    val processingResult = filterModel.processLine(StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action)))
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  @Test
  fun testLogIncorrectEventDataAsError() {
    val action = LogEventAction(eventId)
    action.addData("test", INCORRECT_RULE.description)
    action.addData("project", UNDEFINED_RULE.description)

    val filterModel = StatisticsLogFilterModel()
    val processingResult = filterModel.processLine(StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action)))
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

    doTestCountCollector("{\"dataKey\":[\"1\",\"2\",\"3\"]}", action)
  }

  @Test
  fun testLogCountCollectors() {
    val count = 2
    val action = LogEventAction(eventId, false, count)

    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action))
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" (count=${count}) {}", actual)
  }

  @Test
  fun testLogLineWithCountMatchesRegexpPattern() {
    val action = LogEventAction(eventId, false, 2)

    val logLineWithCount = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action))
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

    val logLineWithCount = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action))
    val matcher = LOG_PATTERN.matcher(logLineWithCount)
    assertTrue(matcher.find())
    assertEquals(eventGroup, matcher.group("groupId"))
    assertEquals(eventId, matcher.group("event"))
    assertEquals("{\"plugin_type\":\"PLATFORM\"}", matcher.group("eventData"))
  }

  private fun doTestCountCollector(expectedEventDataPart: String, action: LogEventAction) {
    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action))
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" $expectedEventDataPart", actual)
  }

  private fun buildLogEvent(action: LogEventAction) = LogEvent("2e5b2e32e061", "193.1801", "176", eventTime,
                                                               eventGroup, groupVersion, "32", action)
}