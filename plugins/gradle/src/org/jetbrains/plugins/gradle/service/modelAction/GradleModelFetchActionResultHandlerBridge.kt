// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.modelAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.application
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.StreamedValueListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionResultHandlerBridge.ModelFetchActionEvent.*
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class GradleModelFetchActionResultHandlerBridge(
  resolverContext: DefaultProjectResolverContext,
  modelFetchAction: GradleModelFetchAction,
  modelFetchActionListener: GradleModelFetchActionListener
) {

  private val modelFetchActionListener = GradleModelFetchActionListenerAdapter(resolverContext, modelFetchAction, modelFetchActionListener)

  private val modelFetchActionEventChannel = Channel<ModelFetchActionEvent>(Channel.UNLIMITED)

  private val isBuildActionInterrupted = AtomicBoolean(true)

  fun asStreamValueListener(): StreamedValueListener {
    return StreamedValueListener { state ->
      modelFetchActionEventChannel.trySend(StreamedValueReceived(state))
    }
  }

  fun asProjectLoadedResultHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      modelFetchActionEventChannel.trySend(ProjectLoaded(state))
    }
  }

  fun asBuildFinishedResultHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      modelFetchActionEventChannel.trySend(BuildFinished(state))
    }
  }

  fun asResultHandler(): ResultHandler<Any> {
    return object : ResultHandler<Any> {
      override fun onComplete(result: Any?) {
        modelFetchActionEventChannel.trySend(ExecutionCompleted(result))
      }

      override fun onFailure(failure: GradleConnectionException) {
        modelFetchActionEventChannel.trySend(ExecutionFailed(failure))
      }
    }
  }

  fun collectAllEvents() {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      // The collector waits for the last event during the Gradle build action execution
      try {
        modelFetchActionEventChannel.receiveAsFlow()
          .collect { event ->
            when (event) {
              is StreamedValueReceived -> onStreamedValueReceived(event)
              is ProjectLoaded -> onProjectLoaded(event)
              is BuildFinished -> onBuildFinished(event)
              is ExecutionCompleted -> onExecutionCompleted(event)
              is ExecutionFailed -> onExecutionFailed(event)
            }
          }
      }
      finally {
        modelFetchActionEventChannel.close()
      }
    }
  }

  private suspend fun onStreamedValueReceived(event: StreamedValueReceived) {
    if (event.value is GradleModelHolderState) {
      modelFetchActionListener.onPhaseCompleted(event.value.phase!!, event.value)
    }
  }

  private suspend fun onProjectLoaded(event: ProjectLoaded) {
    modelFetchActionListener.onProjectLoaded(event.state)
  }

  private suspend fun onBuildFinished(event: BuildFinished) {
    isBuildActionInterrupted.set(false)
    modelFetchActionListener.onBuildCompleted(event.state)
  }

  /**
   * The parameter [ExecutionCompleted.result] will be null
   * if running from the Phased executor as to obtain the models via [asBuildFinishedResultHandler].
   */
  private suspend fun onExecutionCompleted(event: ExecutionCompleted) {
    try {
      if (event.result is GradleModelHolderState) {
        isBuildActionInterrupted.set(false)
        modelFetchActionListener.onBuildCompleted(event.result)
      }
    }
    finally {
      modelFetchActionEventChannel.close()
    }
  }

  private suspend fun onExecutionFailed(event: ExecutionFailed) {
    try {
      modelFetchActionListener.onBuildFailed(event.failure)
    }
    finally {
      /**
       * If we have an exception, pass the failure up to be dealt with by the ExternalSystem
       *
       * But ignore all failures that don't interrupt build action.
       * These failures will be present in the Gradle build output.
       */
      if (isBuildActionInterrupted.get()) {
        modelFetchActionEventChannel.close(event.failure)
      }
      else {
        modelFetchActionEventChannel.close()
      }
    }
  }

  private sealed interface ModelFetchActionEvent {
    class StreamedValueReceived(val value: Any?) : ModelFetchActionEvent
    class ProjectLoaded(val state: GradleModelHolderState) : ModelFetchActionEvent
    class BuildFinished(val state: GradleModelHolderState) : ModelFetchActionEvent
    class ExecutionCompleted(val result: Any?) : ModelFetchActionEvent
    class ExecutionFailed(val failure: GradleConnectionException) : ModelFetchActionEvent
  }
}