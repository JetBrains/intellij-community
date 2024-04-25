// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
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

/**
 * This class handles setting up and running the [BuildActionExecuter] it deals with calling the correct APIs based on the version of
 * Gradle that is present.
 *
 * To do this, we require the current [resolverCtx] and the [buildAction] that should be run.
 *
 * We have two different cases which will be handled in [runBuildAction] we will try the most recent first,
 * falling back to the older ones if a [GradleConnectionException] is thrown. For Gradle 4.8 and above,
 * we use [BuildActionExecuter] in phased mode. This allows us to inject build actions into different parts of the Gradle build.
 * It also allows us to run schedule tasks to be run after fetching the models.
 */
@ApiStatus.Internal
class GradleBuildActionRunner(
  private val resolverCtx: DefaultProjectResolverContext,
  private val buildAction: GradleModelFetchAction,
  private val settings: GradleExecutionSettings,
  private val resultHandler: GradleBuildActionResultHandler
) {

  /**
   * Fetches the Gradle models that have been populated as a result of running the [GradleModelFetchAction] against the Gradle tooling API.
   */
  fun runBuildAction() {
    // Optionally tell Gradle daemon there were recent file changes
    if (Registry.`is`("gradle.report.recently.saved.paths")) {
      notifyConnectionAboutChangedPaths()
    }

    val gradleVersion = resolverCtx.projectGradleVersion
    when {
      // Fallback to default executor for Gradle projects with incorrect build environment or scripts
      gradleVersion == null -> runDefaultBuildAction()

      // Gradle older than 4.8 doesn't support phased execution
      GradleVersionUtil.isGradleOlderThan(gradleVersion, "4.8") -> runDefaultBuildAction()

      else -> runPhasedBuildAction()
    }
  }

  private fun notifyConnectionAboutChangedPaths() {
    ApplicationManager.getApplication()
      .getService(GradleFileModificationTracker::class.java)
      .notifyConnectionAboutChangedPaths(resolverCtx.connection)
  }

  /**
   * Creates the [BuildActionExecuter] to be used to run the [GradleModelFetchAction].
   */
  private fun runPhasedBuildAction() {
    buildAction.isUseProjectsLoadedPhase = true
    resolverCtx.connection.action()
      .projectsLoaded(buildAction, resultHandler.createProjectLoadedHandler())
      .buildFinished(buildAction, resultHandler.createBuildFinishedHandler())
      .build()
      .prepareOperationForSync()
      .withCancellationToken(resolverCtx.cancellationToken)
      .withStreamedValueListener(resultHandler.createStreamValueListener())
      .forTasks(emptyList()) // this will allow setting up Gradle StartParameter#taskNames using model builders
      .run(resultHandler.createResultHandler())
    resultHandler.waitForBuildFinish()
  }

  private fun runDefaultBuildAction() {
    resolverCtx.connection.action(buildAction)
      .prepareOperationForSync()
      .withCancellationToken(resolverCtx.cancellationToken)
      .withStreamedValueListener(resultHandler.createStreamValueListener())
      .run(resultHandler.createResultHandler())
    resultHandler.waitForBuildFinish()
  }

  private fun <T : LongRunningOperation> T.prepareOperationForSync(): T {
    GradleExecutionHelper.prepare(
      resolverCtx.connection,
      this,
      resolverCtx.externalSystemTaskId,
      settings,
      resolverCtx.listener
    )
    GradleOperationHelperExtension.EP_NAME.forEachExtensionSafe {
      it.prepareForSync(this, resolverCtx)
    }
    return this
  }

  private fun <T : BuildActionExecuter<*>> T.withStreamedValueListener(listener: StreamedValueListener): T {
    val gradleVersion = resolverCtx.projectGradleVersion
    if (gradleVersion != null && GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.6")) {
      buildAction.isUseStreamedValues = true
      setStreamedValueListener(listener)
    }
    return this
  }
}
