// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.modelAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.StreamedValueListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.GradleFileModificationTracker
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.statistics.GradleSyncCollector
/**
 * This class handles setting up and running the [BuildActionExecuter] it deals with calling the correct APIs based on the version of
 * Gradle that is present.
 *
 * To do this, we require the current [resolverContext] and the [modelFetchAction] that should be run.
 *
 * We have two different cases which will be handled in [runBuildAction] we will try the most recent first,
 * falling back to the older ones if a [GradleConnectionException] is thrown. For Gradle 4.8 and above,
 * we use [BuildActionExecuter] in phased mode. This allows us to inject build actions into different parts of the Gradle build.
 * It also allows us to run schedule tasks to be run after fetching the models.
 */
@ApiStatus.Internal
class GradleModelFetchActionRunner private constructor(
  private val resolverContext: DefaultProjectResolverContext,
  private val settings: GradleExecutionSettings,
  private val modelFetchAction: GradleModelFetchAction,
  private val modelFetchActionListener: GradleModelFetchActionListener,
) {

  /**
   * Fetches the Gradle models that have been populated as a result of running the [GradleModelFetchAction] against the Gradle tooling API.
   */
  private fun runBuildAction() {
    // Optionally tell Gradle daemon there were recent file changes
    if (Registry.`is`("gradle.report.recently.saved.paths")) {
      notifyConnectionAboutChangedPaths()
    }

    val resultHandler = GradleModelFetchActionResultHandlerBridge(resolverContext, modelFetchAction, modelFetchActionListener)

    val gradleVersion = resolverContext.projectGradleVersion
    when {
      // Fallback to default executor for Gradle projects with incorrect build environment or scripts
      gradleVersion == null -> runDefaultBuildAction(resultHandler)

      // Gradle older than 4.8 doesn't support phased execution
      GradleVersionUtil.isGradleOlderThan(gradleVersion, "4.8") -> runDefaultBuildAction(resultHandler)

      else -> runPhasedBuildAction(resultHandler)
    }

    resultHandler.collectAllEvents()
  }

  private fun notifyConnectionAboutChangedPaths() {
    ApplicationManager.getApplication()
      .getService(GradleFileModificationTracker::class.java)
      .notifyConnectionAboutChangedPaths(resolverContext.connection)
  }

  /**
   * Creates the [BuildActionExecuter] to be used to run the [GradleModelFetchAction].
   */
  private fun runPhasedBuildAction(resultHandler: GradleModelFetchActionResultHandlerBridge) {
    modelFetchAction.isUseProjectsLoadedPhase = true
    resolverContext.connection.action()
      .projectsLoaded(modelFetchAction, resultHandler.asProjectLoadedResultHandler())
      .buildFinished(modelFetchAction, resultHandler.asBuildFinishedResultHandler())
      .build()
      .prepareOperationForSync()
      .withCancellationToken(resolverContext.cancellationToken)
      .withStreamedValueListener(resultHandler.asStreamValueListener())
      .forTasks(emptyList()) // this will allow setting up Gradle StartParameter#taskNames using model builders
      .run(resultHandler.asResultHandler())
  }

  private fun runDefaultBuildAction(resultHandler: GradleModelFetchActionResultHandlerBridge) {
    resolverContext.connection.action(modelFetchAction)
      .prepareOperationForSync()
      .withCancellationToken(resolverContext.cancellationToken)
      .withStreamedValueListener(resultHandler.asStreamValueListener())
      .run(resultHandler.asResultHandler())
  }

  private fun <T : LongRunningOperation> T.prepareOperationForSync(): T {
    GradleExecutionHelper.prepare(
      resolverContext.connection,
      this,
      resolverContext.externalSystemTaskId,
      settings,
      resolverContext.listener
    )
    GradleOperationHelperExtension.EP_NAME.forEachExtensionSafe {
      it.prepareForSync(this, resolverContext)
    }
    return this
  }

  private fun <T : BuildActionExecuter<*>> T.withStreamedValueListener(listener: StreamedValueListener): T {
    if (resolverContext.isStreamingModelFetchingEnabled) {
      modelFetchAction.isUseStreamedValues = true
      setStreamedValueListener(listener)
    }
    return this
  }

  companion object {

    private fun runBuildAction(
      resolverContext: DefaultProjectResolverContext,
      settings: GradleExecutionSettings,
      modelFetchAction: GradleModelFetchAction,
      modelFetchActionListener: GradleModelFetchActionListener,
    ) {
      GradleModelFetchActionRunner(resolverContext, settings, modelFetchAction, modelFetchActionListener).runBuildAction()
    }

    @JvmStatic
    fun runAndTraceBuildAction(
      resolverContext: DefaultProjectResolverContext,
      settings: GradleExecutionSettings,
      modelFetchAction: GradleModelFetchAction,
      modelFetchActionListener: GradleModelFetchActionListener,
    ) {
      GradleSyncCollector.ModelFetchCollector(resolverContext).use { collector ->
        val modelFetchActionListenerWithTrace = object : GradleModelFetchActionListener by modelFetchActionListener {
          override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
            modelFetchActionListener.onModelFetchPhaseCompleted(phase)
            collector.logModelFetchPhaseCompleted(phase)
          }

          override suspend fun onModelFetchFailed(exception: Throwable) {
            modelFetchActionListener.onModelFetchFailed(exception)
            collector.logModelFetchFailure(exception)
          }
        }
        runBuildAction(resolverContext, settings, modelFetchAction, modelFetchActionListenerWithTrace)
      }
    }
  }
}
