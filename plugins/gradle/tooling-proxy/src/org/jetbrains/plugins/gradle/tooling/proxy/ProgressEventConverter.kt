// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.tooling.Failure
import org.gradle.tooling.events.*
import org.gradle.tooling.events.task.*
import org.gradle.tooling.model.UnsupportedMethodException
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.*
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task.*
import java.util.*

class ProgressEventConverter {
  private val descriptorsMap = IdentityHashMap<OperationDescriptor, InternalOperationDescriptor>()

  fun convert(progressEvent: ProgressEvent): ProgressEvent = when (progressEvent) {
    is TaskStartEvent -> progressEvent.run {
      InternalTaskStartEvent(eventTime, displayName, convert(descriptor) as InternalTaskOperationDescriptor)
    }
    is TaskFinishEvent -> progressEvent.run {
      InternalTaskFinishEvent(eventTime, displayName, convert(descriptor) as InternalTaskOperationDescriptor, convert(result))
    }
    is StatusEvent -> progressEvent.run { InternalStatusEvent(eventTime, displayName, convert(descriptor), total, progress, unit) }
    is StartEvent -> progressEvent.run { InternalStartEvent(eventTime, displayName, convert(descriptor)) }
    is FinishEvent -> progressEvent.run { InternalFinishEvent(eventTime, displayName, convert(descriptor), convert(result)) }
    else -> progressEvent
  }

  private fun convert(result: TaskOperationResult?): TaskOperationResult? {
    when (result) {
      null -> return null
      is TaskSuccessResult -> return result.run {
        InternalTaskSuccessResult(startTime, endTime, isUpToDate, isFromCache, taskExecutionDetails())
      }
      is TaskFailureResult -> return result.run {
        InternalTaskFailureResult(startTime, endTime, failures?.map<Failure?, Failure?>(::convert), taskExecutionDetails())
      }
      is TaskSkippedResult -> return result.run { InternalTaskSkippedResult(startTime, endTime, skipMessage) }
      else -> throw IllegalArgumentException("Unsupported task operation result ${result.javaClass}")
    }
  }

  private fun convert(result: OperationResult?): OperationResult? {
    when (result) {
      null -> return null
      is SuccessResult -> return result.run { InternalOperationSuccessResult(startTime, endTime) }
      is FailureResult -> return result.run {
        InternalOperationFailureResult(startTime, endTime, failures?.map<Failure?, Failure?>(::convert))
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

  private fun convert(failure: Failure?): InternalFailure? {
    return failure?.run { InternalFailure(message, description, causes?.map<Failure?, InternalFailure?>(::convert)) }
  }

  private fun convert(operationDescriptor: OperationDescriptor?): InternalOperationDescriptor? {
    if (operationDescriptor == null) return null
    val id = if (operationDescriptor is org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor) operationDescriptor.id.toString() else operationDescriptor.displayName
    return descriptorsMap.getOrPut(operationDescriptor, {
      when (operationDescriptor) {
        is TaskOperationDescriptor -> operationDescriptor.run {
          InternalTaskOperationDescriptor(
            id, name, displayName, convert(parent), taskPath,
            { mutableSetOf<OperationDescriptor>().apply { dependencies.mapNotNullTo(this) { convert(it) } } },
            { convert(originPlugin) })
        }
        else -> operationDescriptor.run { InternalOperationDescriptor(id, name, displayName, convert(parent)) }
      }
    })
  }

  private fun convert(pluginIdentifier: PluginIdentifier?): PluginIdentifier? = when (pluginIdentifier) {
    null -> null
    is BinaryPluginIdentifier -> pluginIdentifier.run { InternalBinaryPluginIdentifier(displayName, className, pluginId) }
    is ScriptPluginIdentifier -> pluginIdentifier.run { InternalScriptPluginIdentifier(displayName, uri) }
    else -> null
  }
}
