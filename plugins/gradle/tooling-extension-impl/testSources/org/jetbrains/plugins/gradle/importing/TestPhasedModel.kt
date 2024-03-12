// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import java.io.Serializable

interface TestPhasedModel : Serializable {

  val phase: GradleModelFetchPhase

  class ProjectLoadedPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_LOADED_PHASE
  }

  class WarmUpPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.WARM_UP_PHASE
  }

  class ProjectSourceSetPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE
  }

  class ProjectSourceSetDependencyPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE
  }

  class ProjectModelPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.PROJECT_MODEL_PHASE
  }

  class AdditionalModelPhase : TestPhasedModel {
    override val phase = GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE
  }

  companion object {

    fun createModel(phase: GradleModelFetchPhase): TestPhasedModel {
      return when (phase) {
        GradleModelFetchPhase.PROJECT_LOADED_PHASE -> ProjectLoadedPhase()
        GradleModelFetchPhase.WARM_UP_PHASE -> WarmUpPhase()
        GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE -> ProjectSourceSetPhase()
        GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE -> ProjectSourceSetDependencyPhase()
        GradleModelFetchPhase.PROJECT_MODEL_PHASE -> ProjectModelPhase()
        GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE -> AdditionalModelPhase()
      }
    }

    fun getModelClass(phase: GradleModelFetchPhase): Class<out TestPhasedModel> {
      return when (phase) {
        GradleModelFetchPhase.PROJECT_LOADED_PHASE -> ProjectLoadedPhase::class.java
        GradleModelFetchPhase.WARM_UP_PHASE -> WarmUpPhase::class.java
        GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE -> ProjectSourceSetPhase::class.java
        GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE -> ProjectSourceSetDependencyPhase::class.java
        GradleModelFetchPhase.PROJECT_MODEL_PHASE -> ProjectModelPhase::class.java
        GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE -> AdditionalModelPhase::class.java
      }
    }
  }
}