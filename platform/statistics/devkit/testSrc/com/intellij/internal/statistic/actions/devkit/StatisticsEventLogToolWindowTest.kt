// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit

import com.intellij.execution.process.ProcessOutputType
import com.intellij.internal.statistic.devkit.toolwindow.StatisticsEventLogMessageBuilder
import com.intellij.internal.statistic.devkit.toolwindow.StatisticsEventLogToolWindow
import com.intellij.internal.statistic.devkit.toolwindow.StatisticsLogFilterModel
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.DateFormatUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup

class StatisticsEventLogToolWindowTest : BasePlatformTestCase() {
  private val eventId = "third.party"
  private val eventTime = 1564643114456
  private val eventGroup = "toolwindow"
  private val groupVersion = "21"
  private lateinit var formattedEventTime: String

  override fun setUp() {
    super.setUp()
    formattedEventTime = DateFormatUtil.formatTimeWithSeconds(eventTime)
  }

  fun testShortenProjectId() {
    val data: MutableMap<String, Any> = hashMapOf(
      "project" to "5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea",
      "plugin_type" to "PLATFORM"
    )
    val action = LogEventAction(eventId, data = data)

    doTestCountCollector("{\"plugin_type\":\"PLATFORM\", \"project\":\"5410c65e...ea\"}", action, data)
  }

  fun testNotShortenProjectId() {
    val projectId = "12345"
    val action = LogEventAction(eventId, data = hashMapOf("project" to projectId))

    doTestCountCollector("{\"project\":\"$projectId\"}", action, hashMapOf("project" to projectId))
  }

  fun testShortenAnonymizedIds() {
    val data: MutableMap<String, Any> = hashMapOf(
      "plugin_type" to "PLATFORM",
      "project" to "74149d0fe0b4e87cd0b0a3e60cefef105d83f26d751f6294d8e565a3352c274b",
      "file_path" to "8a0c68f0f025dfb4c60782cf55ceeeebf513a222af7802312445dd56fa7f8173",
      "login_hash" to "93a3d4443412b6d52ba2ebd2e486772a8767778cce8aae37e4bc549bbb96ec2d",
      "anonymous_id" to "a07abb8885af4858894cb57325d875a636d961f7014c3a93da4b76b033b8e5d8"
    )

    val action = LogEventAction(eventId, data = data)

    doTestCountCollector("{\"anonymous_id\":\"a07abb88...d8\", \"file_path\":\"8a0c68f0...73\", " +
                         "\"login_hash\":\"93a3d444...2d\", \"plugin_type\":\"PLATFORM\", \"project\":\"74149d0f...4b\"}",
                         action, data
    )
  }

  fun testFilterSystemFields() {
    val data: MutableMap<String, Any> = hashMapOf(
      "last" to "1564643442610",
      "created" to "1564643442610"
    )
    val action = LogEventAction(eventId, data = data)

    doTestCountCollector("{}", action, data)
  }

  fun testLogIncorrectEventIdAsError() {
    val incorrectEventId = INCORRECT_RULE.description
    val action = LogEventAction(incorrectEventId)

    val filterModel = StatisticsLogFilterModel()
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$incorrectEventId[$eventId]\" {}", logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  fun testLogIncorrectEventDataAsError() {
    val data: MutableMap<String, Any> = hashMapOf("test" to INCORRECT_RULE.description,
      "project" to UNDEFINED_RULE.description)
    val action = LogEventAction(eventId, data = data)

    val filterModel = StatisticsLogFilterModel()
    val rawData = hashMapOf(
      "test" to "foo",
      "project" to "5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea"
    )
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, rawData)
    val expectedLine = buildExpectedLine(
      "{\"project\":\"validation.undefined_rule[5410c65eafb1f0abd78c6d9bdf33752f13c17b17ed57c3ae26801ae6ee7d17ea]\", " +
      "\"test\":\"validation.incorrect_rule[foo]\"}"
    )
    assertEquals(expectedLine, logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  fun testLogIncorrectEventDataWithoutRawData() {
    val data = mutableMapOf(
      "test" to INCORRECT_RULE.description,
      "project" to UNDEFINED_RULE.description,
      "list" to listOf("foo"),
      "map" to hashMapOf("foo" to "bar")
    )
    val action = LogEventAction(eventId, data = data)
    val filterModel = StatisticsLogFilterModel()
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), null, null)
    val expectedLine = buildExpectedLine(
      "{\"list\":[\"foo\"], \"map\":{\"foo\":\"bar\"}, \"project\":\"validation.undefined_rule\", " +
      "\"test\":\"validation.incorrect_rule\"}"
    )
    assertEquals(expectedLine, logMessage)
    val processingResult = filterModel.processLine(logMessage)
    assertEquals(processingResult.key, ProcessOutputType.STDERR)
  }

  fun testAllValidationTypesUsed() {
    val correctValidationTypes = setOf(ACCEPTED, THIRD_PARTY)
    for (resultType in entries) {
      assertTrue("Don't forget to change toolWindow logic in case of a new value in ValidationResult",
                 StatisticsEventLogToolWindow.rejectedValidationTypes.contains(resultType) || correctValidationTypes.contains(resultType))
    }
  }

  fun testHandleCollectionsInEventData() {
    val action = LogEventAction(eventId, data = hashMapOf("dataKey" to listOf("1", "2", "3")))

    doTestCountCollector("{\"dataKey\":[\"1\",\"2\",\"3\"]}", action, hashMapOf("dataKey" to listOf("1", "2", "3")))
  }

  fun testLogCountCollectors() {
    val count = 2
    val action = LogEventAction(eventId, false, count = count)

    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, emptyMap())
    assertEquals("$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" (count=${count}) {}", actual)
  }

  private fun doTestCountCollector(expectedEventDataPart: String, action: LogEventAction, rawData: Map<String, Any> = emptyMap()) {
    val actual = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), eventId, rawData)
    assertEquals(buildExpectedLine(expectedEventDataPart), actual)
  }

  private fun buildExpectedLine(expectedEventDataPart: String) =
    "$formattedEventTime - [\"$eventGroup\", v$groupVersion]: \"$eventId\" $expectedEventDataPart"

  private fun buildLogEvent(action: LogEventAction, groupId: String = eventGroup) = LogEvent("2e5b2e32e061", "193.1801", "176", eventTime,
                                                                                             LogEventGroup(groupId, groupVersion), "32", action)
}