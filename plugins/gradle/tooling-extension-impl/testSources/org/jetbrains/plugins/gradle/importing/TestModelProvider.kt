// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

class TestModelProvider(
  private val phase: GradleModelFetchPhase,
  private val modelClass: Class<out TestModel>
) : ProjectImportModelProvider {

  constructor(phase: GradleModelFetchPhase) : this(phase, TestPhasedModel.getModelClass(phase))
  constructor(modelClass: Class<out TestModel>) : this(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE, modelClass)

  override fun getPhase(): GradleModelFetchPhase {
    return phase
  }

  override fun populateModels(controller: BuildController, buildModels: Collection<GradleBuild>, modelConsumer: GradleModelConsumer) {
    for (buildModel in buildModels) {
      for (projectModel in buildModel.projects) {
        val model = modelClass.getConstructor().newInstance()
        modelConsumer.consumeProjectModel(projectModel, model, modelClass)
      }
    }
  }
}