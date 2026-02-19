// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.modelAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.GradleConnectionException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext

@ApiStatus.Internal
class GradleModelFetchActionListenerAdapter(
  private val resolverContext: DefaultProjectResolverContext,
  private val modelFetchAction: GradleModelFetchAction,
  private val modelFetchActionListener: GradleModelFetchActionListener
) {

  suspend fun onPhaseCompleted(phase: GradleModelFetchPhase, state: GradleModelHolderState) {
    resolverContext.models.addState(state)

    modelFetchActionListener.onModelFetchPhaseCompleted(phase)
  }

  suspend fun onProjectLoaded(state: GradleModelHolderState) {
    resolverContext.models.addState(state)

    if (!modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.projectLoadedModelFetchPhases) {
        modelFetchActionListener.onModelFetchPhaseCompleted(phase)
      }
    }

    modelFetchActionListener.onProjectLoadedActionCompleted()
  }

  suspend fun onBuildCompleted(state: GradleModelHolderState) {
    resolverContext.models.addState(state)

    if (!modelFetchAction.isUseProjectsLoadedPhase && !modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.projectLoadedModelFetchPhases) {
        modelFetchActionListener.onModelFetchPhaseCompleted(phase)
      }
    }
    if (!modelFetchAction.isUseProjectsLoadedPhase) {
      modelFetchActionListener.onProjectLoadedActionCompleted()
    }
    if (!modelFetchAction.isUseStreamedValues) {
      for (phase in modelFetchAction.buildFinishedModelFetchPhases) {
        modelFetchActionListener.onModelFetchPhaseCompleted(phase)
      }
    }

    modelFetchActionListener.onModelFetchCompleted()
  }

  suspend fun onBuildFailed(exception: GradleConnectionException) {
    modelFetchActionListener.onModelFetchFailed(exception)
  }
}