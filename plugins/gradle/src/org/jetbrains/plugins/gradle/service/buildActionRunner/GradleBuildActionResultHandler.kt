// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class GradleBuildActionResultHandler(
  private val resolverCtx: DefaultProjectResolverContext,
  private val listeners: List<GradleBuildActionListener>
) {

  private val buildFinishWaiter = CountDownLatch(1)

  private val isBuildActionInterrupted = AtomicBoolean(true)
  private val buildFailure = AtomicReference<Throwable>(null)

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

  fun createProjectLoadedHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      try {
        resolverCtx.models.addState(state)
        listeners.forEach { it.onProjectLoaded() }
      }
      catch (e: ProcessCanceledException) {
        resolverCtx.cancellationTokenSource.cancel()
      }
    }
  }

  fun createBuildFinishedHandler(): IntermediateResultHandler<GradleModelHolderState> {
    return IntermediateResultHandler { state ->
      resolverCtx.models.addState(state)
      isBuildActionInterrupted.set(false)
      listeners.forEach { it.onBuildCompleted() }
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
            resolverCtx.models.addState(result)
            isBuildActionInterrupted.set(false)
            listeners.forEach { it.onBuildCompleted() }
          }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }

      override fun onFailure(failure: GradleConnectionException) {
        try {
          buildFailure.set(failure)
          listeners.forEach { it.onBuildFailed(failure) }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }
    }
  }
}