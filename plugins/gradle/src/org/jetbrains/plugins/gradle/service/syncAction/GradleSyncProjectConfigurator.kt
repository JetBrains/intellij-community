// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
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
      performSyncContributors(context, "RESOLVE_PROJECT_INFO_STARTED") {
        onResolveProjectInfoStarted(context, it)
      }
    }
  }

  @JvmStatic
  fun createModelFetchResultHandler(context: DefaultProjectResolverContext): GradleModelFetchActionListener {
    return object : GradleModelFetchActionListener {

      override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
        performSyncContributors(context, phase.name) {
          onModelFetchPhaseCompleted(context, it, phase)
        }
      }

      override suspend fun onModelFetchCompleted() {
        performSyncContributors(context, "MODEL_FETCH_COMPLETED") {
          onModelFetchCompleted(context, it)
        }
      }

      override suspend fun onModelFetchFailed(exception: Throwable) {
        performSyncContributors(context, "MODEL_FETCH_FAILED") {
          onModelFetchFailed(context, it, exception)
        }
      }

      override suspend fun onProjectLoadedActionCompleted() {
        performSyncContributors(context, "PROJECT_LOADED_ACTION") {
          onProjectLoadedActionCompleted(context, it)
        }
      }
    }
  }

  private suspend fun performSyncContributors(
    context: ProjectResolverContext,
    configuratorName: String,
    perform: suspend GradleSyncContributor.(MutableEntityStorage) -> Unit,
  ) {
    configureProject(context, configuratorName) { storage ->
      forEachGradleSyncContributor { syncContributor ->
        checkCanceled()
        TELEMETRY.spanBuilder(syncContributor.name).use {
          syncContributor.perform(storage)
        }
      }
    }
  }

  private suspend fun configureProject(
    context: ProjectResolverContext,
    configuratorName: String,
    configure: suspend (MutableEntityStorage) -> Unit
  ) {
    val externalSystemTaskId = context.externalSystemTaskId
    val externalProjectPath = context.projectPath
    val configuratorDescription = """
      |The Gradle project sync
      |  configuratorName = $configuratorName
      |  externalSystemTaskId = $externalSystemTaskId
      |  externalProjectPath = $externalProjectPath
    """.trimMargin()
    TELEMETRY.spanBuilder(configuratorName).use {
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

  private suspend fun forEachGradleSyncContributor(
    action: suspend (GradleSyncContributor) -> Unit
  ) {
    val contributors = GradleSyncContributor.EP_NAME.extensionList.toMutableList()
    ExternalSystemApiUtil.orderAwareSort(contributors)
    for (contributor in contributors) {
      runCatching { action(contributor) }
        .getOrLogException(LOG)
    }
  }
}