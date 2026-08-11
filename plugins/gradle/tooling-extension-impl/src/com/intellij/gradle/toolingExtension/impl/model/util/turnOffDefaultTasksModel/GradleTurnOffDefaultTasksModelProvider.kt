// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.util.turnOffDefaultTasksModel

import com.intellij.gradle.toolingExtension.modelAction.GradleModelController
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

@Internal
class GradleTurnOffDefaultTasksModelProvider : ProjectImportModelProvider {

  override fun getPhase(): GradleModelFetchPhase =
    GradleModelFetchPhase.TURN_OFF_DEFAULT_TASKS_PHASE

  override fun populateModels(
    modelController: GradleModelController,
    buildModels: Collection<GradleBuild>,
    modelConsumer: GradleModelConsumer,
  ) {
    modelController.fetchModelOrNull(GradleTurnOffDefaultTasksRequest::class.java)
  }
}
