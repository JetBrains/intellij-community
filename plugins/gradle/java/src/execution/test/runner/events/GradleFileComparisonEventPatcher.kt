// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.externalSystem.model.task.event.*
import org.jetbrains.annotations.ApiStatus
import java.util.StringJoiner

typealias TestFinishEvent = ExternalSystemFinishEvent<out TestOperationDescriptor>

/**
 * Gradle TAPI doesn't support providing custom data in test events.
 * So we need to produce events from Gradle init scripts and
 * merge them with TAPI events.
 *
 * Also, see Gradle feature request for FileComparisonFailure
 * `https://github.com/gradle/gradle/issues/24801`
 */
@ApiStatus.Internal
internal class GradleFileComparisonEventPatcher {

  private var isBuiltInTestEventsUsed: Boolean = false
  private val events = HashMap<String, TestEventData>()

  fun setBuiltInTestEventsUsed() {
    isBuiltInTestEventsUsed = true
  }

  fun patchTestFinishEvent(
    event: TestFinishEvent,
    isXml: Boolean
  ): TestFinishEvent? {
    if (!isBuiltInTestEventsUsed) {
      return event
    }
    val patchId = getEventPatchId(event) ?: return event
    val eventData = events[patchId]
    if (eventData == null) {
      events[patchId] = when (isXml) {
        true -> TestEventData(xmlTestEvent = event, tapiTestEvent = null)
        else -> TestEventData(xmlTestEvent = null, tapiTestEvent = event)
      }
      return null
    }
    when (isXml) {
      true -> check(eventData.xmlTestEvent == null) {
        "Found XML test event duplication.\n" + getDebugInfo(event, eventData)
      }
      else -> check(eventData.tapiTestEvent == null) {
        "Found TAPI test event duplication.\n" + getDebugInfo(event, eventData)
      }
    }
    val tapiTestEvent = if (!isXml) event else eventData.tapiTestEvent!!
    val xmlTestEvent = if (isXml) event else eventData.xmlTestEvent!!
    events[patchId] = TestEventData(xmlTestEvent = xmlTestEvent, tapiTestEvent = tapiTestEvent)
    return createTestFinishEvent(tapiTestEvent, xmlTestEvent)
  }

  private fun getDebugInfo(event: TestFinishEvent, eventData: TestEventData): String {
    val xmlTestEvent = eventData.xmlTestEvent
    val tapiTestEvent = eventData.tapiTestEvent
    val joiner = StringJoiner("\n")
    joiner.add("Current event:")
    joiner.add("    ID=" + event.eventId)
    joiner.add("    PARENT_ID=" + event.parentEventId)
    joiner.add("    TIME=" + event.eventTime)
    joiner.add("    NAME=" + event.displayName)
    if (xmlTestEvent != null) {
      joiner.add("XML event:")
      joiner.add("    ID=" + xmlTestEvent.eventId)
      joiner.add("    PARENT_ID=" + xmlTestEvent.parentEventId)
      joiner.add("    TIME=" + xmlTestEvent.eventTime)
      joiner.add("    NAME=" + xmlTestEvent.displayName)
    }
    else {
      joiner.add("XML event = null")
    }
    if (tapiTestEvent != null) {
      joiner.add("TAPI event:")
      joiner.add("    ID=" + tapiTestEvent.eventId)
      joiner.add("    PARENT_ID=" + tapiTestEvent.parentEventId)
      joiner.add("    TIME=" + tapiTestEvent.eventTime)
      joiner.add("    NAME=" + tapiTestEvent.displayName)
    }
    else {
      joiner.add("TAPI event = null")
    }
    return joiner.toString()
  }

  private fun getEventPatchId(event: TestFinishEvent): String? {
    val operationResult = event.operationResult
    if (operationResult is FailureResult) {
      val failure = operationResult.failures.singleOrNull()
      if (failure is TestFailure) {
        if (failure.exceptionName == FILE_COMPARISON_FAILURE) {
          return failure.stackTrace
        }
      }
    }
    return null
  }

  /**
   * Patches [tapiEvent] data by expected/actual data from [xmlEvent].
   * This function makes upgrade only for test events, where file comparison failure is single and root failure.
   * This condition also used inside `FileComparisonTestEventLogger.gradle` which collects file comparison
   * patch data on Gradle side, and transfers it into IDE in XML format.
   *
   * Also, see `org/jetbrains/plugins/gradle/tooling/internal/init/FileComparisonTestEventLogger.gradle`.
   */
  private fun createTestFinishEvent(tapiEvent: TestFinishEvent, xmlEvent: TestFinishEvent): TestFinishEvent {
    val tapiOperationResult = tapiEvent.operationResult
    val xmlOperationResult = xmlEvent.operationResult
    if (tapiOperationResult is FailureResult && xmlOperationResult is FailureResult) {
      val tapiFailure = tapiOperationResult.failures.singleOrNull()
      val xmlFailure = xmlOperationResult.failures.singleOrNull()
      if (tapiFailure is TestFailure && xmlFailure is TestAssertionFailure) {
        val failure = TestAssertionFailure(
          tapiFailure.exceptionName,
          tapiFailure.message,
          tapiFailure.stackTrace,
          tapiFailure.description,
          tapiFailure.causes,
          xmlFailure.expectedText,
          xmlFailure.actualText,
          xmlFailure.expectedFile,
          xmlFailure.actualFile
        )
        val failureResult = FailureResult(
          tapiOperationResult.startTime,
          tapiOperationResult.endTime,
          listOf(failure)
        )
        return ExternalSystemFinishEvent<TestOperationDescriptor>(
          tapiEvent.eventId,
          tapiEvent.parentEventId,
          tapiEvent.descriptor,
          failureResult
        )
      }
    }
    return tapiEvent
  }

  private class TestEventData(
    val xmlTestEvent: TestFinishEvent?,
    val tapiTestEvent: TestFinishEvent?
  )

  companion object {

    private const val FILE_COMPARISON_FAILURE = "com.intellij.rt.execution.junit.FileComparisonFailure"
  }
}
