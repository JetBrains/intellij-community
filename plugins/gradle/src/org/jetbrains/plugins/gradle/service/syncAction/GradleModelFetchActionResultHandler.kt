// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@ApiStatus.Internal
class GradleModelFetchActionResultHandler(
  private val context: ProjectResolverContext
) : GradleModelFetchActionListener {

  override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
    GradleSyncProjectConfigurator.performSyncContributors(context, phase.name) {
      onModelFetchPhaseCompleted(context, it, phase)
    }
  }

  override suspend fun onModelFetchCompleted() {
    GradleSyncProjectConfigurator.performSyncContributors(context, "MODEL_FETCH_COMPLETED") {
      onModelFetchCompleted(context, it)
    }
  }

  override suspend fun onModelFetchFailed(exception: Throwable) {
    GradleSyncProjectConfigurator.performSyncContributors(context, "MODEL_FETCH_FAILED") {
      onModelFetchFailed(context, it, exception)
    }
  }

  override suspend fun onProjectLoadedActionCompleted() {
    GradleSyncProjectConfigurator.performSyncContributors(context, "PROJECT_LOADED_ACTION") {
      onProjectLoadedActionCompleted(context, it)
    }
  }
}