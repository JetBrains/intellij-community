// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeAsync
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeOrdered
import com.intellij.openapi.externalSystem.autolink.mapExtensionSafe
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
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
import java.util.*

private val TELEMETRY: Tracer
  get() = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

private val SYNC_LISTENER: GradleSyncListener
  get() = application.messageBus.syncPublisher(GradleSyncListener.TOPIC)

@ApiStatus.Internal
object GradleSyncProjectConfigurator {

  @JvmStatic
  fun onResolveProjectInfoStarted(context: ProjectResolverContext) {
    GradleSyncActionRunner().performSyncContributorsBlocking(context) {
      it is GradleSyncPhase.Static
    }
  }

  @JvmStatic
  fun createModelFetchResultHandler(context: ProjectResolverContext): GradleModelFetchActionListener {
    return object : GradleModelFetchActionListener {

      private val syncRunner = GradleSyncActionRunner()

      override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
        syncRunner.performSyncContributors(context) {
          it is GradleSyncPhase.Dynamic && it.modelFetchPhase <= phase
        }
        SYNC_LISTENER.onModelFetchPhaseCompleted(context, phase)
      }

      override suspend fun onProjectLoadedActionCompleted() {
        syncRunner.performSyncContributors(context) {
          it is GradleSyncPhase.Dynamic && it.modelFetchPhase is GradleModelFetchPhase.ProjectLoaded
        }
        SYNC_LISTENER.onProjectLoadedActionCompleted(context)
      }

      override suspend fun onModelFetchCompleted() {
        syncRunner.performSyncContributors(context) {
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

private class GradleSyncActionRunner {

  private var lastCompletedPhase: GradleSyncPhase? = null
  private var storage = ImmutableEntityStorage.empty()

  fun performSyncContributorsBlocking(
    context: ProjectResolverContext,
    predicate: (GradleSyncPhase) -> Boolean,
  ) {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      performSyncContributors(context, predicate)
    }
  }

  suspend fun performSyncContributors(
    context: ProjectResolverContext,
    predicate: (GradleSyncPhase) -> Boolean,
  ) {
    val phases = GradleSyncContributor.EP_NAME.mapExtensionSafe { it.phase }
      .filterTo(TreeSet(), predicate)
    for (phase in phases) {
      if (lastCompletedPhase.let { it != null && it >= phase }) continue
      lastCompletedPhase = phase

      performSyncContributors(context, phase)
    }
  }

  private suspend fun performSyncContributors(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ) {
    TELEMETRY.spanBuilder(phase.name).use {
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { contributor ->
        if (contributor.phase == phase) {
          TELEMETRY.spanBuilder(contributor.name).use {
            storage = contributor.createProjectModel(context, storage)
          }
        }
      }
      updateWorkspaceModel(context, phase) { projectBuilder ->
        val syncBuilder = storage.toBuilder()
        GradleSyncExtension.EP_NAME.forEachExtensionSafeOrdered { extension ->
          extension.updateProjectModel(context, syncBuilder, projectBuilder, phase)
        }
      }
      GradleSyncExtension.EP_NAME.forEachExtensionSafeOrdered { extension ->
        extension.updateBridgeModel(context, phase)
      }
      SYNC_LISTENER.onSyncPhaseCompleted(context, phase)
    }
  }

  private suspend fun updateWorkspaceModel(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
    updater: (MutableEntityStorage) -> Unit,
  ) {
    context.project.workspaceModel.update("""
      |The Gradle project sync
      |  phase = $phase
      |  taskId = ${context.taskId}
      |  projectPath = ${context.projectPath}
    """.trimMargin(), updater)
  }
}