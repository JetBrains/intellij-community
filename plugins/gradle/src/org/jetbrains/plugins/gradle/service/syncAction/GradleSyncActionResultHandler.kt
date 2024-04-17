// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeAsync
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
class GradleSyncActionResultHandler(
  private val resolverContext: DefaultProjectResolverContext
) : GradleModelFetchActionListener {

  private val telemetry = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
    configureProject(phase.name) { storage ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onModelFetchPhaseCompleted(resolverContext, storage, phase)
        }
      }
    }
  }

  override suspend fun onModelFetchCompleted() {
    configureProject("MODEL_FETCH_COMPLETED") { storage ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onModelFetchCompleted(resolverContext, storage)
        }
      }
    }
  }

  override suspend fun onModelFetchFailed(exception: Throwable) {
    configureProject("MODEL_FETCH_FAILED") { storage ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use { span ->
          span.setAttribute("exception", exception.javaClass.name)
          span.setAttribute("exception-message", exception.message ?: "null")
          extension.onModelFetchFailed(resolverContext, storage, exception)
        }
      }
    }
  }

  override suspend fun onProjectLoadedActionCompleted() {
    configureProject("PROJECT_LOADED_ACTION") { storage ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onProjectLoadedActionCompleted(resolverContext, storage)
        }
      }
    }
  }

  private suspend fun configureProject(
    configuratorName: @NonNls String,
    configure: suspend (MutableEntityStorage) -> Unit
  ) {
    val externalSystemTaskId = resolverContext.externalSystemTaskId
    val externalProjectPath = resolverContext.projectPath
    val configuratorDescription = """
      |The Gradle project sync
      |  configuratorName = $configuratorName
      |  externalSystemTaskId = $externalSystemTaskId
      |  externalProjectPath = $externalProjectPath
    """.trimMargin()
    telemetry.spanBuilder(configuratorName).use {
      val project = resolverContext.project()
      val workspaceModel = project.workspaceModel
      val storageSnapshot = workspaceModel.currentSnapshot
      val storage = MutableEntityStorage.from(storageSnapshot)
      configure(storage)
      workspaceModel.update(configuratorDescription) {
        it.applyChangesFrom(storage)
      }
    }
  }

  companion object {

    suspend fun ProjectResolverContext.project(): Project {
      return externalSystemTaskId.project()
    }

    private suspend fun ExternalSystemTaskId.project(): Project {
      checkCanceled()
      val project = findProject()
      if (project == null) {
        throw AlreadyDisposedException("Project $ideProjectId is closed")
      }
      return project
    }
  }
}