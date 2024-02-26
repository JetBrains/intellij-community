// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.gradle.toolingExtension.impl.modelAction.AllModels
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
class GradleBuildActionResultHandler(
  private val resolverCtx: ProjectResolverContext,
  private val listeners: List<GradleBuildActionListener>
) {

  private val buildFinishWaiter = CountDownLatch(1)

  /**
   * This queue is used to synchronize the result of the models from the result handler
   * passed to the [org.gradle.tooling.BuildActionExecuter.Builder.projectsLoaded] method of the BuildActionExecutor.
   * Either the result or an exception will be added to the queue in the handler.
   * This will then be picked up and handled by the thread handling sync.
   */
  private val resultQueue = ArrayBlockingQueue<Any>(1)

  /**
   * Every second check to ensure the user didn't cancel the operation.
   * If something goes really wrong with the Gradle connection threads,
   * then at least the user should be able to cancel the refresh process.
   */
  private fun takeQueueResultBlocking(): Any {
    var obtainedResult: Any? = null
    while (obtainedResult == null) {
      resolverCtx.checkCancelled()
      obtainedResult = resultQueue.poll(1, TimeUnit.SECONDS)
    }
    return obtainedResult
  }

  fun getResultBlocking(): AllModels {
    // Wait for the last event during the Gradle build action execution
    ProgressIndicatorUtils.awaitWithCheckCanceled(buildFinishWaiter)
    // Take a result of the Gradle build action execution
    val obtainedResult = takeQueueResultBlocking()
    // If we have an exception, pass the failure up to be dealt with by the ExternalSystem
    if (obtainedResult is Throwable) {
      throw obtainedResult
    }
    // If we have a result, return it
    return obtainedResult as AllModels
  }

  fun createProjectLoadedHandler(): IntermediateResultHandler<AllModels> {
    return IntermediateResultHandler { models ->
      try {
        listeners.forEach { it.onProjectLoaded(models) }
      }
      catch (e: ProcessCanceledException) {
        resolverCtx.cancellationTokenSource?.cancel()
      }
    }
  }

  fun createBuildFinishedHandler(): IntermediateResultHandler<AllModels> {
    return IntermediateResultHandler { models ->
      resultQueue.add(models)
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
          if (result != null) {
            resultQueue.add(result)
            listeners.forEach { it.onBuildCompleted() }
          }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }

      override fun onFailure(failure: GradleConnectionException) {
        try {
          resultQueue.add(failure)
          listeners.forEach { it.onBuildFailed(failure) }
        }
        finally {
          buildFinishWaiter.countDown()
        }
      }
    }
  }
}