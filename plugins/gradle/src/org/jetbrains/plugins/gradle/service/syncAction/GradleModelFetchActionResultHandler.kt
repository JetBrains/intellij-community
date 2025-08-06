// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionListener
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext

@ApiStatus.Internal
class GradleModelFetchActionResultHandler(
  private val context: DefaultProjectResolverContext,
) : GradleModelFetchActionListener {

  override suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
    // With older Gradle versions, buildSrc has its own separate resolve (as opposed to being a composite build) and this causes issues.
    // As of now, it's simpler to just skip the sync contributors in these cases.
    if (Registry.`is`("gradle.phased.sync.bridge.disabled") && context.isBuildSrcProject) return

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