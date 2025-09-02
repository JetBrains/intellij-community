// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.diagnostic.logging.LogFilterModel.MyProcessingResult
import com.intellij.execution.process.ProcessOutputType
import com.intellij.internal.statistic.devkit.toolwindow.StatisticsEventLogFilter.Companion.LOG_PATTERN
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.DateFormatUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup

class StatisticsEventLogFormatterTest : BasePlatformTestCase() {
  companion object {
    private const val MAGENTA_START = "\u001B[38;2;102;14;122m"
    private const val GREEN_START = "\u001B[38;2;0;128;0m"
    private const val COLOR_END = "\u001B[0m"
    private const val EVENT_ID = "third.party"
    private const val EVENT_GROUP = "toolwindow"
    private const val GROUP_VERSION = "21"
    private const val EVENT_TIME = 1564643114456
  }

  private lateinit var formattedEventTime: String

  private lateinit var oneLineLogEmptyEventData: String
  private lateinit var oneLineLogSeveralEventData: String
  private lateinit var oneLineLogSeveralEqualEvents: String
  private lateinit var oneLineLogArrayEventData: String
  private lateinit var oneLineLogObjectEventDataValue: String
  private lateinit var oneLineLogNestedObjectEventDataValue: String

  private lateinit var formatedOneLineLogSeveralEventData: String
  private lateinit var formatedOneLineLogSeveralEqualEvents: String
  private lateinit var formatedOneLineLogArrayEventData: String
  private lateinit var formatedOneLineLogObjectEventDataValue: String
  private lateinit var formatedOneLineLogNestedObjectEventDataValue: String

  private lateinit var multiLineLogEmptyEventData: String
  private lateinit var formatedMultiLineLogSeveralEventData: String
  private lateinit var formatedMultiLineLogSeveralEqualEvents: String
  private lateinit var formatedMultiLineLogArrayEventData: String
  private lateinit var formatedMultiLineLogObjectEventDataValue: String
  private lateinit var formatedMultiLineLogNestedObjectEventDataValue: String

  private lateinit var multiLineLogSeveralEventData: String
  private lateinit var multiLineLogSeveralEqualEvents: String
  private lateinit var multiLineLogArrayEventData: String
  private lateinit var multiLineLogObjectEventDataValue: String
  private lateinit var multiLineLogNestedObjectEventDataValue: String

  override fun setUp() {
    super.setUp()
    formattedEventTime = DateFormatUtil.formatTimeWithSeconds(EVENT_TIME)
    oneLineLogEmptyEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {}"
    oneLineLogSeveralEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {\"field1\":\"test\", \"field2\":500}"
    oneLineLogSeveralEqualEvents = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" (count=12) {\"field1\":\"test\", \"field2\":500}"
    oneLineLogArrayEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {\"field1\":[1,2,3]}"
    oneLineLogObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {\"parent\":{\"field1\":\"test\",\"field2\":500}}"
    oneLineLogNestedObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {\"parent\":{\"field1\":{\"field1\":\"test\",\"field2\":500},\"field2\":{\"field1\":\"test\",\"field2\":500}}}"

    formatedOneLineLogSeveralEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START\"test\"$COLOR_END, $MAGENTA_START\"field2\"$COLOR_END:${GREEN_START}500$COLOR_END}"
    formatedOneLineLogSeveralEqualEvents = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" (count=12) {$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START\"test\"$COLOR_END, $MAGENTA_START\"field2\"$COLOR_END:${GREEN_START}500$COLOR_END}"
    formatedOneLineLogArrayEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START[1,2,3]$COLOR_END}"
    formatedOneLineLogObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {$MAGENTA_START\"parent\"$COLOR_END:$GREEN_START{\"field1\":\"test\",\"field2\":500}$COLOR_END}"
    formatedOneLineLogNestedObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" {$MAGENTA_START\"parent\"$COLOR_END:$GREEN_START{\"field1\":{\"field1\":\"test\",\"field2\":500},\"field2\":{\"field1\":\"test\",\"field2\":500}}$COLOR_END}"

    multiLineLogEmptyEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n}"
    formatedMultiLineLogSeveralEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START\"test\"$COLOR_END,\n\t$MAGENTA_START\"field2\"$COLOR_END:${GREEN_START}500$COLOR_END\n}"
    formatedMultiLineLogSeveralEqualEvents = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" (count=12)\n{\n\t$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START\"test\"$COLOR_END,\n\t$MAGENTA_START\"field2\"$COLOR_END:${GREEN_START}500$COLOR_END\n}"
    formatedMultiLineLogArrayEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t$MAGENTA_START\"field1\"$COLOR_END:$GREEN_START[1,2,3]$COLOR_END\n}"
    formatedMultiLineLogObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t$MAGENTA_START\"parent\"$COLOR_END:$GREEN_START{\"field1\":\"test\",\"field2\":500}$COLOR_END\n}"
    formatedMultiLineLogNestedObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t$MAGENTA_START\"parent\"$COLOR_END:$GREEN_START{\"field1\":{\"field1\":\"test\",\"field2\":500},\"field2\":{\"field1\":\"test\",\"field2\":500}}$COLOR_END\n}"

    multiLineLogSeveralEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t\"field1\":\"test\",\n\t\"field2\":500\n}"
    multiLineLogSeveralEqualEvents = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\" (count=12)\n{\n\t\"field1\":\"test\",\n\t\"field2\":500\n}"
    multiLineLogArrayEventData = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t\"field1\":[1,2,3]\n}"
    multiLineLogObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t\"parent\":{\"field1\":\"test\",\"field2\":500}\n}"
    multiLineLogNestedObjectEventDataValue = "$formattedEventTime - [\"$EVENT_GROUP\", v$GROUP_VERSION]: \"$EVENT_ID\"\n{\n\t\"parent\":{\"field1\":{\"field1\":\"test\",\"field2\":500},\"field2\":{\"field1\":\"test\",\"field2\":500}}\n}"
  }

  /**
   * Check that the one-line log message with empty event data matches the log regexp pattern.
   */
  fun testLogWithoutEventDataMatchesLogPattern() {
    val action = LogEventAction(EVENT_ID)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogEmptyEventData.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with empty event data -> formated one-line log with empty event data if stream is output, log presentation is one-line.
   */
  fun testLogWithoutEventDataFormatToOutputOneLine() {
    val action = LogEventAction(EVENT_ID)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(oneLineLogEmptyEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with empty event data -> one-line log with empty event data if stream is error, log presentation is one-line.
   */
  fun testLogWithoutEventDataFormatToErrorOneLine() {
    val action = LogEventAction(EVENT_ID)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogEmptyEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with empty event data -> formated multiline log with empty event data if stream is output, log presentation is multiline.
   */
  fun testLogWithoutEventDataFormatToOutputMultiline() {
    val action = LogEventAction(EVENT_ID)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(multiLineLogEmptyEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with empty event data -> multiline log with empty event data if stream is error, log presentation is multiline.
   */
  fun testLogWithoutEventDataFormatToErrorMultiline() {
    val action = LogEventAction(EVENT_ID)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogEmptyEventData, formatedLogMessage)
  }

  /**
   * Check that the one-line log message with event data matches the log regexp pattern
   */
  fun testLogWithEventDataMatchesLogPattern() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogSeveralEventData.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with event data -> formated one-line log with event data if stream is output, log presentation is one-line.
   */
  fun testLogWithEventDataFormatToOutputOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(formatedOneLineLogSeveralEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data -> one-line log with event data if stream is error, log presentation is one-line.
   */
  fun testLogWithEventDataFormatToErrorOneLIne() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogSeveralEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data -> formated multiline log with event data if stream is output, log presentation is multiline.
   */
  fun testLogWithEventDataFormatToOutputMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(formatedMultiLineLogSeveralEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data -> multiline log with event data if stream is error, log presentation is multiline.
   */
  fun testLogWithEventDataFormatToErrorMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogSeveralEventData, formatedLogMessage)
  }

  /**
   * Check that the one-line log message for one event with list event data value matches the log regexp pattern
   */
  fun testLogWithListEventDataValueMatchesLogPattern() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to listOf(1, 2, 3))
    val action = LogEventAction(EVENT_ID, data = data)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogArrayEventData.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with list event data value -> formated one-line log with list event data value if stream is output,
   * log presentation is one-line.
   */
  fun testLogWithListEventDataValueFormatToOutputOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to listOf(1, 2, 3))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(formatedOneLineLogArrayEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with list event data value -> one-line log with list event data value if stream is an error,
   * log presentation is one-line.
   */
  fun testLogWithListEventDataValueFormatToErrorOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to listOf(1, 2, 3))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogArrayEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with list event data value -> formated multiline log with list event data value if stream is output,
   * log presentation is multiline.
   */
  fun testLogWithListEventDataValueFormatToOutputMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to listOf(1, 2, 3))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(formatedMultiLineLogArrayEventData, formatedLogMessage)
  }

  /**
   * Check a one-line log with list event data value -> multiline log with list event data value if stream is an error,
   * log presentation is multiline.
   */
  fun testLogWithListEventDataValueFormatToErrorMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to listOf(1, 2, 3))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogArrayEventData, formatedLogMessage)
  }

  /**
   * Check that the one-line log message for one event with event data object value matches the log regexp pattern
   */
  fun testLogWithEventDataObjectValueMatchesLogPattern() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf("field1" to "test", "field2" to 500))
    val action = LogEventAction(EVENT_ID, data = data)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogObjectEventDataValue.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with event data object value -> formated one-line log with event data object value if stream is output,
   * log presentation is one-line.
   */
  fun testLogWithEventDataObjectValueFormatToOutputOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf("field1" to "test", "field2" to 500))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(formatedOneLineLogObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data object value -> one-line log with event data object value if stream is an error,
   * log presentation is one-line.
   */
  fun testLogWithEventDataObjectValueFormatToErrorOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf("field1" to "test", "field2" to 500))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data object value -> formated multiline log with event data object value if stream is output,
   * log presentation is multiline.
   */
  fun testLogWithEventDataObjectValueFormatToOutputMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf("field1" to "test", "field2" to 500))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(formatedMultiLineLogObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with event data object value -> multiline log with event data object value if stream is error,
   * log presentation is multiline.
   */
  fun testLogWithEventDataObjectValueFormatToErrorMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf("field1" to "test", "field2" to 500))
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check that the one-line log message for one event with nested object event data value matches the log regexp pattern
   */
  fun testLogWithEventDataNestedObjectValueMatchesLogPattern() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf(
      "field1" to hashMapOf("field1" to "test", "field2" to 500),
      "field2" to hashMapOf("field1" to "test", "field2" to 500))
    )
    val action = LogEventAction(EVENT_ID, data = data)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogNestedObjectEventDataValue.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with nested object event data value -> formated one-line log with nested object event data value if stream is output,
   * log presentation is one-line.
   */
  fun testLogWithEventDataNestedObjectValueFormatToOutputOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf(
      "field1" to hashMapOf("field1" to "test", "field2" to 500),
      "field2" to hashMapOf("field1" to "test", "field2" to 500))
    )
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(formatedOneLineLogNestedObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with nested object event data value -> one-line log with nested object event data value if stream is error,
   * log presentation is one-line.
   */
  fun testLogWithEventDataNestedObjectValueFormatToErrorOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf(
      "field1" to hashMapOf("field1" to "test", "field2" to 500),
      "field2" to hashMapOf("field1" to "test", "field2" to 500))
    )
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogNestedObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with nested object event data value -> formated multiline log with nested object event data value if stream is output,
   * log presentation is multiline.
   */
  fun testLogWithEventDataNestedObjectValueFormatToOutputMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf(
      "field1" to hashMapOf("field1" to "test", "field2" to 500),
      "field2" to hashMapOf("field1" to "test", "field2" to 500))
    )
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(formatedMultiLineLogNestedObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check a one-line log with nested object event data value -> multiline log with nested object event data value if stream is error,
   * log presentation is multiline.
   */
  fun testLogWithEventDataNestedObjectValueFormatToErrorMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("parent" to hashMapOf(
      "field1" to hashMapOf("field1" to "test", "field2" to 500),
      "field2" to hashMapOf("field1" to "test", "field2" to 500))
    )
    val action = LogEventAction(EVENT_ID, data = data)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogNestedObjectEventDataValue, formatedLogMessage)
  }

  /**
   * Check that the one-line log message for several equal events with event data matches the log regexp pattern
   */
  fun testLogForSeveralEqualEventsMatchesLogPattern() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data, count = 12)
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val matcher = LOG_PATTERN.matcher(logMessage)
    assertTrue(matcher.find())
    val expectedLogMessage = oneLineLogSeveralEqualEvents.substring(oneLineLogSeveralEqualEvents.indexOf(" - ")+ 3)
    assertEquals(expectedLogMessage, matcher.group())
  }

  /**
   * Check a one-line log with several equal events with event data -> formated one-line log with several equal events with event data
   * if stream is output, log presentation is one-line.
   */
  fun testLogForSeveralEqualEventsFormatToOutputOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data, count = 12)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, false)
    assertEquals(formatedOneLineLogSeveralEqualEvents, formatedLogMessage)
  }

  /**
   * Check a one-line log with several equal events with event data -> one-line log with several equal events with event data
   * if stream is error, log presentation is one-line.
   */
  fun testLogForSeveralEqualEventsFormatToErrorOneLine() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data, count = 12)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, false)
    assertEquals(oneLineLogSeveralEqualEvents, formatedLogMessage)
  }

  /**
   * Check a one-line log with several equal events with event data -> formated multiline log with several equal events with event data
   * if stream is output, log presentation is multiline.
   */
  fun testLogForSeveralEqualEventsFormatToOutputMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data, count = 12)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDOUT, true)
    assertEquals(formatedMultiLineLogSeveralEqualEvents, formatedLogMessage)
  }

  /**
   * Check a one-line log with several equal events with event data -> multiline log with several equal events with event data
   * if stream is error, log presentation is multiline.
   */
  fun testLogForSeveralEqualEventsFormatToErrorMultiline() {
    val data: MutableMap<String, Any> = hashMapOf("field1" to "test", "field2" to 500)
    val action = LogEventAction(EVENT_ID, data = data, count = 12)
    val formatedLogMessage = getFormatedMessage(action, ProcessOutputType.STDERR, true)
    assertEquals(multiLineLogSeveralEqualEvents, formatedLogMessage)
  }

  private fun getFormatedMessage(action: LogEventAction, outputType: ProcessOutputType, isMultilineLog: Boolean): String? {
    val logMessage = StatisticsEventLogMessageBuilder().buildLogMessage(buildLogEvent(action), EVENT_ID, emptyMap())
    val processingResult = MyProcessingResult(outputType, true, null)
    val logFilterModel = TestStatisticsLogFilterModel(processingResult)
    val formatter = StatisticsEventLogFormatter(logFilterModel)
    formatter.updateLogPresentation(isMultilineLog)
    return formatter.formatMessage(logMessage)
  }

  private fun buildLogEvent(action: LogEventAction, groupId: String = EVENT_GROUP) =
    LogEvent("2e5b2e32e061", "193.1801", "176", EVENT_TIME,
             LogEventGroup(groupId, GROUP_VERSION), "32", action)
}

private class TestStatisticsLogFilterModel(private val processingResult: MyProcessingResult) : StatisticsLogFilterModel() {
  override fun processLine(line: String): MyProcessingResult {
    return processingResult
  }
}
