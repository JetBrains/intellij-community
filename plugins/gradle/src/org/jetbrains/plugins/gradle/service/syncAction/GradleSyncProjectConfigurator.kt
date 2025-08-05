// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
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
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
object GradleSyncProjectConfigurator {

  private val LOG = logger<GradleSyncProjectConfigurator>()
  private val TELEMETRY = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  @JvmStatic
  fun onResolveProjectInfoStarted(context: ProjectResolverContext) {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      getGradleSyncContributors()
        .filter { (phase, _) -> phase is GradleSyncPhase.Static }
        .forEach { (phase, contributors) -> performSyncContributors(context, phase, contributors) }
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
        application.messageBus.syncPublisher(GradleSyncListener.TOPIC)
          .onProjectLoadedActionCompleted(context)
      }

      override suspend fun onModelFetchCompleted() {
        performSyncContributors<GradleModelFetchPhase.BuildFinished>()
        application.messageBus.syncPublisher(GradleSyncListener.TOPIC)
          .onModelFetchCompleted(context)
      }

      override suspend fun onModelFetchFailed(exception: Throwable) {
        application.messageBus.syncPublisher(GradleSyncListener.TOPIC)
          .onModelFetchFailed(context, exception)
      }

      private suspend fun performSyncContributors(completedPhase: GradleModelFetchPhase) {
        getGradleSyncContributors()
          .filter { (phase, _) -> phase is GradleSyncPhase.Dynamic && phase.modelFetchPhase <= completedPhase }
          .forEach { (phase, contributors) -> performSyncContributorsIfNeeded(context, phase, contributors) }
      }

      private suspend inline fun <reified T : GradleModelFetchPhase> performSyncContributors() {
        getGradleSyncContributors()
          .filter { (phase, _) -> phase is GradleSyncPhase.Dynamic && phase.modelFetchPhase is T }
          .forEach { (phase, contributors) -> performSyncContributorsIfNeeded(context, phase, contributors) }
      }

      private var lastCompletedPhase: GradleSyncPhase? = null

      private suspend fun performSyncContributorsIfNeeded(
        context: ProjectResolverContext,
        phase: GradleSyncPhase,
        contributors: Sequence<GradleSyncContributor>,
      ) {
        if (lastCompletedPhase.let { it == null || it < phase }) {
          lastCompletedPhase = phase
          performSyncContributors(context, phase, contributors)
        }
      }
    }
  }

  private suspend fun performSyncContributors(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
    contributors: Sequence<GradleSyncContributor>,
  ) {
    configureProject(context, phase) { storage ->
      for (contributor in contributors) {
        checkCanceled()
        LOG.runAndLogException {
          TELEMETRY.spanBuilder(contributor.name).use {
            contributor.configureProjectModel(context, storage)
          }
        }
      }
    }
    application.messageBus.syncPublisher(GradleSyncListener.TOPIC)
      .onSyncPhaseCompleted(context, phase)
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

  private fun getGradleSyncContributors(): Sequence<Pair<GradleSyncPhase, Sequence<GradleSyncContributor>>> {
    val result = HashMap<GradleSyncPhase, MutableSet<GradleSyncContributor>>()
    GradleSyncContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result.computeIfAbsent(contributor.phase) { HashSet() }
        .add(contributor)
    }
    return result.asSequence()
      .sortedBy { (phase, _) -> phase }
      .map { (phase, contributors) ->
        phase to contributors.asSequence()
          .sortedWith(ExternalSystemApiUtil.ORDER_AWARE_COMPARATOR)
      }
  }
}