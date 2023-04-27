// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.externalSystem.model.task.event.*
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus

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

  private var isGradleTestEventsUsed: Boolean = false
  private val events = HashMap<String, TestFinishEvent>()

  fun setGradleTestEventsUsed() {
    isGradleTestEventsUsed = true
  }

  fun patchTestFinishEvent(
    event: TestFinishEvent,
    isXml: Boolean
  ): TestFinishEvent? {
    if (!isGradleTestEventsUsed) {
      return event
    }
    val patchId = getEventPatchId(event) ?: return event
    val storedEvent = events[patchId]
    if (storedEvent == null) {
      events[patchId] = event
      return null
    }
    val tapiEvent = if (isXml) storedEvent else event
    val xmlEvent = if (isXml) event else storedEvent
    return createTestFinishEvent(tapiEvent, xmlEvent)
  }

  private fun getEventPatchId(event: TestFinishEvent): String? {
    val operationResult = event.operationResult
    if (operationResult is FailureResult) {
      return getFailuresPatchId(operationResult.failures)
    }
    return null
  }

  private fun getFailuresPatchId(failure: List<Failure>): String? {
    return failure.mapNotNull { getFailurePatchId(it) }
      .joinToString("========================================\n")
      .nullize()
  }

  private fun getFailurePatchId(failure: Failure): String? {
    if (failure is TestFailure) {
      if (failure.exceptionName == "com.intellij.rt.execution.junit.FileComparisonFailure") {
        return failure.stackTrace
      }
    }
    return getFailuresPatchId(failure.causes)
  }

  private fun createTestFinishEvent(tapiEvent: TestFinishEvent, xmlEvent: TestFinishEvent): TestFinishEvent {
    return ExternalSystemFinishEventImpl<TestOperationDescriptor>(
      tapiEvent.eventId,
      tapiEvent.parentEventId,
      tapiEvent.descriptor,
      createOperationResult(tapiEvent.operationResult, xmlEvent.operationResult)
    )
  }

  private fun createOperationResult(tapiOperationResult: OperationResult, xmlOperationResult: OperationResult): OperationResult {
    if (tapiOperationResult is FailureResult && xmlOperationResult is FailureResult) {
      return FailureResultImpl(
        tapiOperationResult.startTime,
        tapiOperationResult.endTime,
        createFailures(tapiOperationResult.failures, xmlOperationResult.failures)
      )
    }
    return tapiOperationResult
  }

  private fun createFailures(tapiFailures: List<Failure>, xmlFailures: List<Failure>): List<Failure> {
    val failures = ArrayList<Failure>()
    val xmlFailuresIndex = xmlFailures.associateBy { getFailurePatchId(it) }
    for (tapiFailure in tapiFailures) {
      val patchId = getFailurePatchId(tapiFailure)
      val xmlFailure = xmlFailuresIndex[patchId]
      if (xmlFailure != null) {
        val failure = createFailure(tapiFailure, xmlFailure)
        failures.add(failure)
      }
      else {
        failures.add(tapiFailure)
      }
    }
    return failures
  }

  private fun createFailure(tapiFailure: Failure, xmlFailure: Failure): Failure {
    if (tapiFailure is TestAssertionFailure && xmlFailure is TestAssertionFailure) {
      return TestAssertionFailure(
        tapiFailure.exceptionName,
        tapiFailure.message,
        tapiFailure.stackTrace,
        tapiFailure.description,
        createFailures(tapiFailure.causes, xmlFailure.causes),
        xmlFailure.expectedText,
        xmlFailure.actualText,
        xmlFailure.expectedFile,
        xmlFailure.actualFile
      )
    }
    return tapiFailure
  }
}
