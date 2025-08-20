// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeAsync
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.util.application
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants

private val TELEMETRY: Tracer
  get() = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

private val SYNC_LISTENER: GradleSyncListener
  get() = application.messageBus.syncPublisher(GradleSyncListener.TOPIC)

@ApiStatus.Internal
object GradleSyncProjectConfigurator {

  @JvmStatic
  fun onResolveProjectInfoStarted(context: ProjectResolverContext) {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      GradleSyncContributorRunner().performSyncContributors(context) { it is GradleSyncPhase.Static }
    }
  }

  @JvmStatic
  fun createModelFetchResultHandler(context: ProjectResolverContext): GradleModelFetchActionListener {
    return object : GradleModelFetchActionListener {

      private val runner = GradleSyncContributorRunner()

      override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
        runner.performSyncContributors(context) {
          it is GradleSyncPhase.Dynamic && it.modelFetchPhase <= phase
        }
        SYNC_LISTENER.onModelFetchPhaseCompleted(context, phase)
      }

      override suspend fun onProjectLoadedActionCompleted() {
        runner.performSyncContributors(context) {
          it is GradleSyncPhase.Dynamic && it.modelFetchPhase is GradleModelFetchPhase.ProjectLoaded
        }
        SYNC_LISTENER.onProjectLoadedActionCompleted(context)
      }

      override suspend fun onModelFetchCompleted() {
        runner.performSyncContributors(context) {
          it is GradleSyncPhase.Dynamic && it.modelFetchPhase is GradleModelFetchPhase.BuildFinished
        }
        SYNC_LISTENER.onModelFetchCompleted(context)
      }

      override suspend fun onModelFetchFailed(exception: Throwable) {
        SYNC_LISTENER.onModelFetchFailed(context, exception)
      }
    }
  }
}

private class GradleSyncContributorRunner {

  private var lastCompletedPhase: GradleSyncPhase? = null
  private var storage = ImmutableEntityStorage.empty()

  suspend fun performSyncContributors(
    context: ProjectResolverContext,
    predicate: (GradleSyncPhase) -> Boolean,
  ) {
    GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { contributor ->
      if (predicate(contributor.phase)) {
        performSyncContributors(context, contributor.phase)
      }
    }
  }

  private suspend fun performSyncContributors(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ) {
    if (lastCompletedPhase.let { it != null && it >= phase }) return
    lastCompletedPhase = phase

    TELEMETRY.spanBuilder(phase.name).use {
      storage = createProjectModel(context, storage, phase)
        .also { updateProjectModel(context, it, phase) }
        .also { SYNC_LISTENER.onSyncPhaseCompleted(context, phase) }
    }
  }

  private suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
    phase: GradleSyncPhase,
  ): ImmutableEntityStorage {
    var result = storage
    GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { contributor ->
      if (contributor.phase == phase) {
        TELEMETRY.spanBuilder(contributor.name).use {
          result = contributor.createProjectModel(context, result)
        }
      }
    }
    return result
  }

  private suspend fun updateProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    val configuratorDescription = """
      |The Gradle project sync
      |  phase = $phase
      |  taskId = ${context.taskId}
      |  projectPath = ${context.projectPath}
    """.trimMargin()
    context.project.workspaceModel.update(configuratorDescription) { projectBuilder ->
      val projectStorage = projectBuilder.toSnapshot()
      val syncBuilder = storage.toBuilder()
      GradleSyncExtension.EP_NAME.forEachExtensionSafeAsync { extension ->
        extension.updateSyncStorage(context, syncBuilder, projectStorage, phase)
      }
      val syncStorage = syncBuilder.toSnapshot()
      GradleSyncExtension.EP_NAME.forEachExtensionSafeAsync { extension ->
        extension.updateProjectStorage(context, syncStorage, projectBuilder, phase)
      }
    }
    GradleSyncExtension.EP_NAME.forEachExtensionSafeAsync { extension ->
      extension.updateBridgeModel(context, phase)
    }
  }
}