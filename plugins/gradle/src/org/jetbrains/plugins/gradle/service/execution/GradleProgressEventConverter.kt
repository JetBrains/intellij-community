// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.FinishEventImpl
import com.intellij.build.events.impl.ProgressBuildEventImpl
import com.intellij.build.events.impl.StartEventImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.*
import com.intellij.openapi.externalSystem.model.task.event.OperationResult
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PathUtil
import org.gradle.tooling.events.*
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.SkippedResult
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.events.test.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
object GradleProgressEventConverter {

  private val LOG = Logger.getInstance("com.intellij.openapi.externalSystem.event-processing")
  private val FILE_COMPARISON_CONTENT_CHARSET: Charset = StandardCharsets.UTF_8

  @JvmStatic
  fun createTaskNotificationEvent(
    taskId: ExternalSystemTaskId,
    operationId: String,
    event: ProgressEvent,
  ): ExternalSystemTaskNotificationEvent? {
    return when {
      isSkipped(event.descriptor) -> null
      event is TaskProgressEvent -> convertTaskProgressEvent(event, taskId, operationId)
      else -> convertTestProgressEvent(event, taskId, operationId)
    }
  }

  @JvmStatic
  fun legacyConvertProgressBuildEvent(
    taskId: ExternalSystemTaskId,
    id: Any,
    event: String,
  ): ExternalSystemTaskNotificationEvent? {
    val operationName = legacyConvertBuildEventDisplayName(event) ?: return null
    val esEvent = ProgressBuildEventImpl(id, null, 0, "$operationName...", -1L, -1L, "")
    return ExternalSystemBuildEvent(taskId, esEvent)
  }

  @JvmStatic
  fun legacyConvertBuildEventDisplayName(eventDescription: String): @NlsSafe String? {
    return when {
      eventDescription.startsWith("Download ") -> {
        val path = eventDescription.substring("Download ".length)
        GradleBundle.message("progress.title.download", PathUtil.getFileName(path))
      }
      eventDescription.startsWith("Task: ") -> GradleBundle.message("progress.title.run.tasks")
      eventDescription.startsWith("Build model ") -> GradleBundle.message("progress.title.build.model")
      eventDescription.startsWith("Build parameterized model") -> GradleBundle.message("progress.title.build.model")
      eventDescription.startsWith("Configure project ") -> GradleBundle.message("progress.title.configure.projects")
      eventDescription.startsWith("Cross-configure project ") -> GradleBundle.message("progress.title.configure.projects")
      eventDescription == "Build" -> GradleBundle.message("progress.title.build")
      else -> null
    }
  }

  @JvmStatic
  fun legacyConvertTaskNotificationEvent(taskId: ExternalSystemTaskId, event: String): ExternalSystemTaskNotificationEvent {
    return ExternalSystemTaskNotificationEvent(taskId, event)
  }

  private fun convertTaskProgressEvent(
    event: TaskProgressEvent,
    taskId: ExternalSystemTaskId,
    operationId: String,
  ): ExternalSystemTaskNotificationEvent {
    val eventId = createEventId(event.descriptor, operationId)
    val eventTime = event.eventTime
    val message = event.descriptor.name
    return when (event) {
      is TaskStartEvent -> ExternalSystemBuildEvent(taskId, StartEventImpl(eventId, taskId, eventTime, message))
      is StatusEvent -> ExternalSystemBuildEvent(taskId, ProgressBuildEventImpl(eventId, taskId, eventTime,
                                                                                message, event.total, event.progress,
                                                                                event.unit))
      is TaskFinishEvent -> {
        val eventResult = convertTaskProgressEventResult(event.result)
        if (eventResult != null) {
          ExternalSystemBuildEvent(taskId, FinishEventImpl(eventId, taskId, eventTime, message, eventResult))
        }
        else {
          LOG.warn("Unsupported TaskFinish event ${event.javaClass.getSimpleName()} $event")
          ExternalSystemTaskNotificationEvent(taskId, event.descriptor.name)
        }
      }
      else -> {
        LOG.warn("Undefined Gradle event ${event.javaClass.getSimpleName()} $event")
        ExternalSystemTaskNotificationEvent(taskId, event.descriptor.name)
      }
    }
  }

  private fun convertTaskProgressEventResult(result: org.gradle.tooling.events.OperationResult): EventResult? {
    return when (result) {
      is SuccessResult -> com.intellij.build.events.impl.SuccessResultImpl(result is TaskSuccessResult && result.isUpToDate)
      is FailureResult -> com.intellij.build.events.impl.FailureResultImpl(null, null)
      is SkippedResult -> com.intellij.build.events.impl.SkippedResultImpl()
      else -> {
        LOG.warn("Undefined operation result ${result.javaClass.getSimpleName()} $result")
        return null
      }
    }
  }

  private fun convertTestProgressEvent(
    event: ProgressEvent,
    taskId: ExternalSystemTaskId,
    operationId: String,
  ): ExternalSystemTaskNotificationEvent? {
    val eventId = createEventId(event.descriptor, operationId)
    val parentEventId = event.descriptor.parent?.let {
      createEventId(it, operationId)
    }
    val descriptor = convertTestDescriptor(event)
    return when (event) {
      is TestStartEvent -> {
        val esEvent = ExternalSystemStartEvent<TestOperationDescriptor>(eventId, parentEventId, descriptor)
        ExternalSystemTaskExecutionEvent(taskId, esEvent)
      }
      is TestFinishEvent -> {
        val result = event.result
        val operationResult = convertTestProgressEventResult(result)
        val esEvent = ExternalSystemFinishEvent<TestOperationDescriptor>(eventId, parentEventId, descriptor, operationResult)
        ExternalSystemTaskExecutionEvent(taskId, esEvent)
      }
      is TestOutputEvent -> {
        val outputDescriptor = event.descriptor
        val destination = outputDescriptor.destination
        val isStdOut = destination == Destination.StdOut
        val message = outputDescriptor.message
        val description = (if (isStdOut) "StdOut" else "StdErr") + message
        val esEvent = ExternalSystemMessageEvent<TestOperationDescriptor>(eventId, parentEventId, descriptor, isStdOut, message, description)
        ExternalSystemTaskExecutionEvent(taskId, esEvent)
      }
      else -> null
    }
  }

  private fun convertTestProgressEventResult(result: TestOperationResult): OperationResult {
    val startTime = result.startTime
    val endTime = result.endTime
    return when (result) {
      is TestSuccessResult -> com.intellij.openapi.externalSystem.model.task.event.SuccessResult(startTime, endTime, false)
      is TestFailureResult -> {
        val failures = result.failures.map { it -> convertTestFailure(it) }
        com.intellij.openapi.externalSystem.model.task.event.FailureResult(startTime, endTime, failures)
      }
      is TestSkippedResult -> com.intellij.openapi.externalSystem.model.task.event.SkippedResult(startTime, endTime)
      else -> {
        LOG.warn("Undefined test operation result ${result.javaClass.getName()}")
        com.intellij.openapi.externalSystem.model.task.event.OperationResult(startTime, endTime)
      }
    }
  }

  private fun convertTestFailure(failure: org.gradle.tooling.Failure): Failure {
    val message = failure.message
    val description = failure.description
    val causes = failure.causes.map { it -> convertTestFailure(it) }

    if (failure is org.gradle.tooling.FileComparisonTestAssertionFailure) {
      val exceptionName = failure.className
      val stackTrace = failure.stacktrace

      val expectedContent = failure.expectedContent
      val expectedText = if (expectedContent != null) String(expectedContent, FILE_COMPARISON_CONTENT_CHARSET) else failure.expected
      val expectedFile = if (expectedContent != null) failure.expected else null

      val actualContent = failure.actualContent
      val actualText = if (actualContent != null) String(actualContent, FILE_COMPARISON_CONTENT_CHARSET) else failure.actual
      val actualFile = if (actualContent != null) failure.actual else null

      if (expectedText != null && actualText != null) {
        return TestAssertionFailure(exceptionName, message, stackTrace, description, causes, expectedText, actualText, expectedFile, actualFile)
      }
    }
    if (failure is org.gradle.tooling.TestAssertionFailure) {
      val exceptionName = failure.className
      val stackTrace = failure.stacktrace
      val expectedText = failure.expected
      val actualText = failure.actual
      if (expectedText != null && actualText != null) {
        return TestAssertionFailure(exceptionName, message, stackTrace, description, causes, expectedText, actualText);
      }
    }
    if (failure is org.gradle.tooling.TestFailure) {
      val exceptionName = failure.className
      val stackTrace = failure.stacktrace
      return TestFailure(exceptionName, message, stackTrace, description, causes, false)
    }
    LOG.warn("Undefined test failure type " + failure.javaClass.name)
    return com.intellij.openapi.externalSystem.model.task.event.Failure(message, description, Collections.emptyList());
  }

  private fun convertTestDescriptor(event: ProgressEvent): com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor {
    val descriptor = event.descriptor
    val eventTime = event.eventTime
    val displayName = descriptor.displayName
    return when (descriptor) {
      is JvmTestOperationDescriptor -> {
        val suiteName = descriptor.suiteName
        val className = descriptor.className
        val methodName = descriptor.methodName
        com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor(displayName, eventTime, suiteName, className, methodName)
      }
      else -> com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor(displayName, eventTime, null, null, null)
    }
  }

  private fun isSkipped(descriptor: OperationDescriptor): Boolean {
    val name = descriptor.displayName
    return isMissedProgressEvent(name) || isUnnecessaryTestProgressEvent(name)
  }

  private fun isUnnecessaryTestProgressEvent(name: String): Boolean {
    return name.startsWith("Gradle Test Executor") || name.startsWith("Gradle Test Run")
  }

  private fun isMissedProgressEvent(name: String): Boolean {
    return when {
      name.startsWith("Execute executeTests for") -> true
      name.startsWith("Executing task") -> true
      name.startsWith("Run tasks") -> true
      name.startsWith("Run main tasks") -> true
      name.startsWith("Run build") -> true
      else -> false
    }
  }

  private fun createEventId(descriptor: OperationDescriptor, operationId: String): String {
    val joiner = StringJoiner(" > ")
    joiner.add("[$operationId]")
    var currentDescriptor: OperationDescriptor? = descriptor
    while (currentDescriptor != null) {
      if (!isSkipped(currentDescriptor)) {
        joiner.add("[${currentDescriptor.displayName}]")
      }
      currentDescriptor = currentDescriptor.parent
    }
    return joiner.toString()
  }
}
