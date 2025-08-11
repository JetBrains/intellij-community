// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeOrdered
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
object GradleSyncProjectConfigurator {

  private val TELEMETRY = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  private val syncListener: GradleSyncListener
    get() = application.messageBus.syncPublisher(GradleSyncListener.TOPIC)

  private fun getGradleSyncPhases(): Sequence<GradleSyncPhase> {
    val result = HashSet<GradleSyncPhase>()
    GradleSyncContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result.add(contributor.phase)
    }
    return result.asSequence().sorted()
  }

  @JvmStatic
  fun onResolveProjectInfoStarted(context: ProjectResolverContext) {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      getGradleSyncPhases()
        .filterIsInstance<GradleSyncPhase.Static>()
        .forEach { performSyncContributors(context, it) }
    }
  }

  @JvmStatic
  fun createModelFetchResultHandler(context: DefaultProjectResolverContext): GradleModelFetchActionListener {
    return object : GradleModelFetchActionListener {

      override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
        performSyncContributors(phase)
      }

      override suspend fun onProjectLoadedActionCompleted() {
        performSyncContributors<GradleModelFetchPhase.ProjectLoaded>()
        syncListener.onProjectLoadedActionCompleted(context)
      }

      override suspend fun onModelFetchCompleted() {
        performSyncContributors<GradleModelFetchPhase.BuildFinished>()
        syncListener.onModelFetchCompleted(context)
      }

      override suspend fun onModelFetchFailed(exception: Throwable) {
        syncListener.onModelFetchFailed(context, exception)
      }

      private suspend fun performSyncContributors(completedPhase: GradleModelFetchPhase) {
        getGradleSyncPhases()
          .filterIsInstance<GradleSyncPhase.Dynamic>()
          .filter { it.modelFetchPhase <= completedPhase }
          .forEach { performSyncContributorsIfNeeded(context, it) }
      }

      private suspend inline fun <reified T : GradleModelFetchPhase> performSyncContributors() {
        getGradleSyncPhases()
          .filterIsInstance<GradleSyncPhase.Dynamic>()
          .filter { it.modelFetchPhase is T }
          .forEach { performSyncContributorsIfNeeded(context, it) }
      }

      private var lastCompletedPhase: GradleSyncPhase? = null

      private suspend fun performSyncContributorsIfNeeded(context: ProjectResolverContext, phase: GradleSyncPhase) {
        if (lastCompletedPhase.let { it == null || it < phase }) {
          lastCompletedPhase = phase
          performSyncContributors(context, phase)
        }
      }
    }
  }

  private suspend fun performSyncContributors(context: ProjectResolverContext, phase: GradleSyncPhase) {
    configureProject(context, phase) { storage ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeOrdered { contributor ->
        if (contributor.phase == phase) {
          TELEMETRY.spanBuilder(contributor.name).use {
            contributor.configureProjectModel(context, storage)
          }
        }
      }
    }
    syncListener.onSyncPhaseCompleted(context, phase)
  }

  private suspend fun configureProject(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
    configure: suspend (MutableEntityStorage) -> Unit,
  ) {
    val configuratorDescription = """
      |The Gradle project sync
      |  phase = $phase
      |  taskId = ${context.taskId}
      |  projectPath = ${context.projectPath}
    """.trimMargin()
    TELEMETRY.spanBuilder(phase.name).use {
      checkCanceled()
      val project = context.project
      val workspaceModel = project.workspaceModel
      val storageSnapshot = workspaceModel.currentSnapshot
      val storage = MutableEntityStorage.from(storageSnapshot)
      configure(storage)
      workspaceModel.update(configuratorDescription) {
        it.applyChangesFrom(storage)
      }
    }
  }
}