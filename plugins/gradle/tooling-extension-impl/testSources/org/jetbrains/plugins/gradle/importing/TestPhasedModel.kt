// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase

interface TestPhasedModel : TestModel {

  val phase: GradleModelFetchPhase

  class ProjectLoadedPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_LOADED_PHASE
  }

  class ProjectModelPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_MODEL_PHASE
  }

  class ProjectSourceSetPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE
  }

  class ProjectSourceSetDependencyPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE
  }

  class AdditionalModelPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE
  }

  companion object {

    fun getModelClass(phase: GradleModelFetchPhase): Class<out TestPhasedModel> {
      return when (phase) {
        GradleModelFetchPhase.PROJECT_LOADED_PHASE -> ProjectLoadedPhase::class.java
        GradleModelFetchPhase.PROJECT_MODEL_PHASE -> ProjectModelPhase::class.java
        GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE -> ProjectSourceSetPhase::class.java
        GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE -> ProjectSourceSetDependencyPhase::class.java
        GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE -> AdditionalModelPhase::class.java
      }
    }
  }
}