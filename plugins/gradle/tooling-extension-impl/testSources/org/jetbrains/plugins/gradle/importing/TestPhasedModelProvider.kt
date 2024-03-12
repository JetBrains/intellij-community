// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

class TestPhasedModelProvider(
  private val phase: GradleModelFetchPhase
) : ProjectImportModelProvider {

  override fun getPhase(): GradleModelFetchPhase {
    return phase
  }

  override fun populateProjectModels(controller: BuildController, projectModel: BasicGradleProject, modelConsumer: GradleModelConsumer) {
    val model = TestPhasedModel.createModel(phase)
    val modelClass = TestPhasedModel.getModelClass(phase)
    modelConsumer.consumeProjectModel(projectModel, model, modelClass)
  }
}