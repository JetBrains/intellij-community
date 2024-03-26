// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.StreamedValueListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class GradleBuildActionResultHandler(
  private val resolverCtx: DefaultProjectResolverContext,
  private val buildAction: GradleModelFetchAction,
  private val buildActionMulticaster: GradleBuildActionListener
) {

  private val buildFinishWaiter = CountDownLatch(1)

  private val isBuildActionInterrupted = AtomicBoolean(true)
  private val buildFailure = AtomicReference<Throwable>(null)

  private fun onPhaseCompleted(phase: GradleModelFetchPhase, state: GradleModelHolderState) {
    resolverCtx.models.addState(state)
    buildActionMulticaster.onPhaseCompleted(phase)
  }

  private fun onProjectLoaded(state: GradleModelHolderState) {
    resolverCtx.models.addState(state)

    if (!buildAction.isUseStreamedValues) {
      for (phase in buildAction.projectLoadedModelProviders.keys) {
        buildActionMulticaster.onPhaseCompleted(phase)
      }
    }

    buildActionMulticaster.onProjectLoaded()
  }

  private fun onBuildCompleted(state: GradleModelHolderState) {
    resolverCtx.models.addState(state)
    isBuildActionInterrupted.set(false)

    if (!buildAction.isUseProjectsLoadedPhase && !buildAction.isUseStreamedValues) {
      for (phase in buildAction.projectLoadedModelProviders.keys) {
        buildActionMulticaster.onPhaseCompleted(phase)
      }
    }
    if (!buildAction.isUseProjectsLoadedPhase) {
      buildActionMulticaster.onProjectLoaded()
    }
    if (!buildAction.isUseStreamedValues) {
      for (phase in buildAction.buildFinishedModelProviders.keys) {
        buildActionMulticaster.onPhaseCompleted(phase)
      }
    }

    buildActionMulticaster.onBuildCompleted()
  }

  private fun onBuildFailed(failure: GradleConnectionException) {
    buildFailure.set(failure)
    buildActionMulticaster.onBuildFailed(failure)
  }

  fun waitForBuildFinish() {
    // Wait for the last event during the Gradle build action execution
    ProgressIndicatorUtils.awaitWithCheckCanceled(buildFinishWaiter)

    /**
     * If we have an exception, pass the failure up to be dealt with by the ExternalSystem
     *
     * But ignore all failures that don't interrupt build action.
     * These failures will be present in the Gradle build output.
     */
    val buildFailure = buildFailure.get()
    if (buildFailure != null && isBuildActionInterrupted.get()) {
      throw buildFailure
    }
  }

  fun createStreamValueListener(): StreamedValueListener {
    return StreamedValueListener { state ->
      if (state is GradleModelHolderState) {
        runCancellable {
          onPhaseCompleted(state.phase!!, state)
        }
      }
    }
  }

  fun createProjectLoadedHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      runCancellable {
        onProjectLoaded(state)
      }
    }
  }

  fun createBuildFinishedHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      runCancellable {
        onBuildCompleted(state)
      }
    }
  }

  fun createResultHandler(): ResultHandler<Any> {
    return object : ResultHandler<Any> {

      /**
       * The parameter [result] will be null if running from the Phased executer as to obtain the models via [createBuildFinishedHandler].
       * However, if it is not null then we must be running from the normal build action excuter and thus the [result] must be
       * added to the queue to unblock the main thread.
       */
      override fun onComplete(result: Any?) {
        try {
          if (result is GradleModelHolderState) {
            runCancellable {
              onBuildCompleted(result)
            }
          }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }

      override fun onFailure(failure: GradleConnectionException) {
        try {
          runCancellable {
            onBuildFailed(failure)
          }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }
    }
  }

  private fun runCancellable(action: () -> Unit) {
    try {
      if (!resolverCtx.isCancellationRequested) {
        action()
      }
    }
    catch (e: ProcessCanceledException) {
      resolverCtx.cancel()
    }
  }
}