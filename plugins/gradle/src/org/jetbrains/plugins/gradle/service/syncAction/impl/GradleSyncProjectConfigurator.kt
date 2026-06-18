// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchFailure
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
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.TreeSet

private val TELEMETRY: Tracer
  get() = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

private val SYNC_LISTENER: GradleSyncListener
  get() = application.messageBus.syncPublisher(GradleSyncListener.TOPIC)

@Internal
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

      // Model fetch callbacks can cover overlapping phase ranges.
      // The shared runner keeps phase execution ordered and at most once.
      // If retry support is introduced in the future, it should be explicit in this handler.
      private val syncRunner = GradleSyncActionRunner()

      private val syncFailureHandler = GradleSyncFailureHandler()

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

      override suspend fun onModelFetchFailures(failures: List<GradleModelFetchFailure>) {
        syncFailureHandler.reportSyncFailures(context, failures)
      }
    }
  }
}

private class GradleSyncActionRunner {

  private var lastClaimedPhase: GradleSyncPhase? = null
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
      // Claim each phase before execution. Later callbacks skip already claimed phases.
      if (lastClaimedPhase.let { it != null && it >= phase }) continue
      lastClaimedPhase = phase

      performSyncContributors(context, phase)
    }
  }

  private suspend fun performSyncContributors(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ) {
    TELEMETRY.spanBuilder(phase.name + "-idea").use {
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

@Internal
const val GRADLE_SYNC_FAILURE_GROUP: String = "gradle.sync.failure.group"

private class GradleSyncFailureHandler {

  fun reportSyncFailures(context: ProjectResolverContext, failures: List<GradleModelFetchFailure>) {
    for (failure in failures) {
      context.reporter.failure(createIssueFailure(failure))
        .withSuppressed(true)
        .withGroup(GRADLE_SYNC_FAILURE_GROUP)
        .report()
    }
  }

  private fun createIssueFailure(failure: GradleModelFetchFailure): GradleIssueFailure =
    GradleIssueFailure.createIssueFailure(
      message = failure.message,
      description = failure.description,
      causes = failure.causes.map { createIssueFailure(it) }
    )
}
