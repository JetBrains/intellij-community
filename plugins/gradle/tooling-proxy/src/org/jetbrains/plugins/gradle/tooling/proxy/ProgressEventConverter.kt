// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.tooling.Failure
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.problems.ProblemGroup
import org.gradle.tooling.events.problems.ProblemSummary
import org.gradle.tooling.events.problems.internal.DefaultProblemSummariesEvent
import org.gradle.tooling.events.task.TaskExecutionResult
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationResult
import org.gradle.tooling.events.test.TestOutputDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestSkippedResult
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.test.TestSuccessResult
import org.gradle.tooling.model.UnsupportedMethodException
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalBinaryPluginIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFinishEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationDescriptor
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationFailureResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationSuccessResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalProblemSummariesEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalScriptPluginIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalStartEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalStatusEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblemGroup
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblemId
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblemSummary
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskExecutionDetails
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskFailureResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskFinishEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskOperationDescriptor
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskSkippedResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskStartEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.InternalTaskSuccessResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalJvmTestOperationDescriptor
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestFailureResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestFinishEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestOperationDescriptor
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestOutputDescriptor
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestOutputEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestSkippedResult
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestStartEvent
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test.InternalTestSuccessResult
import java.util.IdentityHashMap

class ProgressEventConverter {
  private val descriptorsMap = IdentityHashMap<OperationDescriptor, InternalOperationDescriptor>()

  fun convert(progressEvent: ProgressEvent): ProgressEvent = when (progressEvent) {
    is TaskStartEvent -> progressEvent.run {
      InternalTaskStartEvent(eventTime, displayName, convert(descriptor) as InternalTaskOperationDescriptor)
    }
    is TaskFinishEvent -> progressEvent.run {
      InternalTaskFinishEvent(eventTime, displayName, convert(descriptor) as InternalTaskOperationDescriptor, convert(result))
    }
    is TestStartEvent -> progressEvent.run { InternalTestStartEvent(eventTime, displayName, convert(descriptor)) }
    is TestFinishEvent -> progressEvent.run { InternalTestFinishEvent(eventTime, displayName, convert(descriptor), convert(result)) }
    is TestOutputEvent -> progressEvent.run { InternalTestOutputEvent(eventTime, convert(descriptor) as InternalTestOutputDescriptor) }
    is StatusEvent -> progressEvent.run { InternalStatusEvent(eventTime, displayName, convert(descriptor), total, progress, unit) }
    is StartEvent -> progressEvent.run { InternalStartEvent(eventTime, displayName, convert(descriptor)) }
    is FinishEvent -> progressEvent.run { InternalFinishEvent(eventTime, displayName, convert(descriptor), convert(result)) }
    is DefaultProblemSummariesEvent -> progressEvent.run {
      InternalProblemSummariesEvent(eventTime, displayName, convert(descriptor), convert(problemSummaries))
    }
    else -> progressEvent
  }

  private fun convert(summaries: List<ProblemSummary>?): List<ProblemSummary> {
    return summaries?.map { problem ->
      val problemId = problem.problemId.let { id -> InternalProblemId(id.name, id.displayName, convert(id.group)) }
      InternalProblemSummary(problemId, problem.count)
    } ?: ArrayList()
  }

  private fun convert(group: ProblemGroup?): InternalProblemGroup? {
    return group?.let { InternalProblemGroup(it.name, it.displayName, convert(it.parent)) }
  }

  private fun convert(result: TaskOperationResult?): TaskOperationResult? {
    when (result) {
      null -> return null
      is TaskSuccessResult -> return result.run {
        InternalTaskSuccessResult(startTime, endTime, isUpToDate, isFromCache, taskExecutionDetails())
      }
      is TaskFailureResult -> return result.run {
        InternalTaskFailureResult(startTime, endTime, failures?.map<Failure?, Failure?>(::toInternalFailure), taskExecutionDetails())
      }
      is TaskSkippedResult -> return result.run { InternalTaskSkippedResult(startTime, endTime, skipMessage) }
      else -> throw IllegalArgumentException("Unsupported task operation result ${result.javaClass}")
    }
  }

  private fun convert(result: TestOperationResult?): TestOperationResult? {
    when (result) {
      null -> return null
      is TestSuccessResult -> return result.run { InternalTestSuccessResult(startTime, endTime) }
      is TestSkippedResult -> return result.run { InternalTestSkippedResult(startTime, endTime) }
      is TestFailureResult -> return result.run { InternalTestFailureResult(startTime, endTime, failures?.map<Failure?, Failure?>(::toInternalFailure)) }
      else -> throw IllegalArgumentException("Unsupported test operation result ${result.javaClass}")
    }
  }

  private fun convert(result: OperationResult?): OperationResult? {
    when (result) {
      null -> return null
      is SuccessResult -> return result.run { InternalOperationSuccessResult(startTime, endTime) }
      is FailureResult -> return result.run {
        InternalOperationFailureResult(startTime, endTime, failures?.map<Failure?, Failure?>(::toInternalFailure))
      }
      else -> throw IllegalArgumentException("Unsupported operation result ${result.javaClass}")
    }
  }

  private fun TaskExecutionResult.taskExecutionDetails(): InternalTaskExecutionDetails? = try {
    InternalTaskExecutionDetails.of(isIncremental, executionReasons)
  }
  catch (e: UnsupportedMethodException) {
    InternalTaskExecutionDetails.unsupported()
  }

  private fun convert(operationDescriptor: OperationDescriptor?): InternalOperationDescriptor? {
    if (operationDescriptor == null) return null
    val id = if (operationDescriptor is org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor) operationDescriptor.id.toString() else operationDescriptor.displayName
    return descriptorsMap.getOrPut(operationDescriptor) {
      when (operationDescriptor) {
        is TaskOperationDescriptor -> operationDescriptor.run {
          InternalTaskOperationDescriptor(
            id, name, displayName, convert(parent), taskPath,
            { mutableSetOf<OperationDescriptor>().apply { dependencies.mapNotNullTo(this) { convert(it) } } },
            { convert(originPlugin) })
        }
        is JvmTestOperationDescriptor -> operationDescriptor.run {
          InternalJvmTestOperationDescriptor(id, name, displayName, convert(parent),
                                             jvmTestKind, suiteName, className, methodName)
        }
        is TestOperationDescriptor -> operationDescriptor.run {
          InternalTestOperationDescriptor(id, name, displayName, convert(parent))
        }
        is TestOutputDescriptor -> operationDescriptor.run {
          InternalTestOutputDescriptor(id, name, displayName, convert(parent), destination, message)
        }
        else -> operationDescriptor.run { InternalOperationDescriptor(id, name, displayName, convert(parent)) }
      }
    }
  }

  private fun convert(pluginIdentifier: PluginIdentifier?): PluginIdentifier? = when (pluginIdentifier) {
    null -> null
    is BinaryPluginIdentifier -> pluginIdentifier.run { InternalBinaryPluginIdentifier(displayName, className, pluginId) }
    is ScriptPluginIdentifier -> pluginIdentifier.run { InternalScriptPluginIdentifier(displayName, uri) }
    else -> null
  }
}
