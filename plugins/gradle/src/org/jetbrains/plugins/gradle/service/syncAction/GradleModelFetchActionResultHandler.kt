// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.GradleConnectionException
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext

class GradleModelFetchActionResultHandler(
  private val resolverContext: DefaultProjectResolverContext,
  private val modelFetchAction: GradleModelFetchAction,
  private val resultHandler: GradleSyncActionResultHandler
) {

  suspend fun onPhaseCompleted(phase: GradleModelFetchPhase, state: GradleModelHolderState) {
    resolverContext.models.addState(state)
    resultHandler.onModelFetchPhaseCompleted(phase)
  }

  suspend fun onProjectLoaded(state: GradleModelHolderState) {
    resolverContext.models.addState(state)

    if (!modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.projectLoadedModelProviders.keys) {
        resultHandler.onModelFetchPhaseCompleted(phase)
      }
    }

    resultHandler.onProjectLoadedActionCompleted()
  }

  suspend fun onBuildCompleted(state: GradleModelHolderState) {
    resolverContext.models.addState(state)

    if (!modelFetchAction.isUseProjectsLoadedPhase && !modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.projectLoadedModelProviders.keys) {
        resultHandler.onModelFetchPhaseCompleted(phase)
      }
    }
    if (!modelFetchAction.isUseProjectsLoadedPhase) {
      resultHandler.onProjectLoadedActionCompleted()
    }
    if (!modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.buildFinishedModelProviders.keys) {
        resultHandler.onModelFetchPhaseCompleted(phase)
      }
    }

    resultHandler.onModelFetchCompleted()
  }

  suspend fun onBuildFailed(exception: GradleConnectionException) {
    resultHandler.onModelFetchFailed(exception)
  }
}