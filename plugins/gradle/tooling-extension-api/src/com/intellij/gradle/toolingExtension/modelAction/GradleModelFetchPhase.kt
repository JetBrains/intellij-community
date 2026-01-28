// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.io.Serializable

/**
 * Represents a possible Gradle model fetch phase.
 *
 * Provider-backed phases are executed only when at least one [org.jetbrains.plugins.gradle.model.ProjectImportModelProvider]
 * is registered for them. The Gradle model fetch action is the only component that owns this guarantee;
 * declaring a provider-backed phase constant does not schedule it by itself.
 *
 * [BASE_SCRIPT_MODEL_PHASE] is an internal action-owned phase emitted directly by the Gradle model fetch action.
 */
@Experimental
@NonExtendable
sealed interface GradleModelFetchPhase : Comparable<GradleModelFetchPhase>, Serializable {

  /**
   * This name is used for the Gradle model fetch identification.
   */
  val name: String

  /**
   * In this phase, Gradle fetches models available before Gradle has loaded projects.
   *
   * @see org.gradle.tooling.BuildActionExecuter.setStreamedValueListener
   */
  sealed interface BaseScript : GradleModelFetchPhase

  /**
   * In these phases, Gradle model providers are executed when the Gradle has loaded projects.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder.projectsLoaded
   * @see org.gradle.tooling.IntermediateResultHandler
   * @see org.gradle.tooling.BuildActionExecuter.setStreamedValueListener
   */
  @NonExtendable
  sealed interface ProjectLoaded : GradleModelFetchPhase {

    val order: Int

    companion object {

      operator fun invoke(order: Int, name: String): ProjectLoaded {
        return GradleProjectLoadedModelFetchPhase(order, name)
      }
    }
  }

  /**
   * In these phases, Gradle model providers are executed when all Gradle tasks are run.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder.buildFinished
   * @see org.gradle.tooling.IntermediateResultHandler
   * @see org.gradle.tooling.BuildActionExecuter.setStreamedValueListener
   */
  @NonExtendable
  sealed interface BuildFinished : GradleModelFetchPhase {

    val order: Int

    companion object {

      operator fun invoke(order: Int, name: String): BuildFinished {
        return GradleBuildFinishedModelFetchPhase(order, name)
      }
    }
  }

  companion object {

    /**
     * In this phase, Gradle fetches a base classpath model for Gradle script files.
     * This enables early IDE support for Gradle scripts even if the full Gradle sync fails.
     */
    @JvmField
    val BASE_SCRIPT_MODEL_PHASE: GradleModelFetchPhase = GradleBaseScriptModelFetchPhase

    /**
     * In this phase, Gradle model providers fetch Gradle tooling models after gradle projects are loaded and before "sync" tasks are run.
     * This can be used to set up "sync" tasks for the import
     */
    @Internal
    @JvmField
    val PROJECT_LOADED_PHASE: GradleModelFetchPhase = ProjectLoaded(0, "PROJECT_LOADED_PHASE")

    @Internal
    @JvmField
    val TURN_OFF_DEFAULT_TASKS_PHASE: GradleModelFetchPhase = ProjectLoaded(1, "TURN_OFF_DEFAULT_TASKS_PHASE")

    /**
     * In this phase, Gradle model providers fetch a Gradle project identification models.
     */
    @JvmField
    val PROJECT_MODEL_PHASE: GradleModelFetchPhase = BuildFinished(0, "PROJECT_MODEL_PHASE")

    /**
     * In this phase, Gradle model providers fetch a Gradle project source set models.
     */
    @JvmField
    val PROJECT_SOURCE_SET_PHASE: GradleModelFetchPhase = BuildFinished(1000, "SOURCE_SET_MODEL_PHASE")

    /**
     * In this phase, Gradle model providers:
     *  * Configure dependency download policies;
     *  * Resolve dependencies for the project source sets.
     */
    @JvmField
    val PROJECT_SOURCE_SET_DEPENDENCY_PHASE: GradleModelFetchPhase = BuildFinished(2000, "DEPENDENCY_MODEL_PHASE")

    /**
     * In this phase, Gradle model providers fetch a full classpath model for Gradle script files.
     */
    @JvmField
    val SCRIPT_MODEL_PHASE: GradleModelFetchPhase = BuildFinished(3000, "SCRIPT_MODEL_PHASE")

    /**
     * In this phase, Gradle model providers fetch rest of Gradle models, which needed for rich experience in IntelliJ IDEA.
     * It is a code insight in Gradle scripts, data for run configuration creation and for code completion in him,
     * data for code profiling, etc.
     */
    @JvmField
    val ADDITIONAL_MODEL_PHASE: GradleModelFetchPhase = BuildFinished(4000, "ADDITIONAL_MODEL_PHASE")
  }
}

// Implementation

private data object GradleBaseScriptModelFetchPhase : GradleModelFetchPhase.BaseScript {

  override val name: String = "BASE_SCRIPT_MODEL_PHASE"

  override fun toString(): String = name

  override fun compareTo(other: GradleModelFetchPhase): Int {
    return when (other) {
      is GradleBaseScriptModelFetchPhase -> 0
      is GradleProjectLoadedModelFetchPhase,
      is GradleBuildFinishedModelFetchPhase -> -1
    }
  }
}

private class GradleProjectLoadedModelFetchPhase(
  override val order: Int,
  override val name: String,
) : GradleModelFetchPhase.ProjectLoaded {

  override fun toString(): String = name

  override fun compareTo(other: GradleModelFetchPhase): Int {
    return when (other) {
      is GradleBaseScriptModelFetchPhase -> 1
      is GradleProjectLoadedModelFetchPhase -> order.compareTo(other.order)
      is GradleBuildFinishedModelFetchPhase -> -1
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleProjectLoadedModelFetchPhase) return false

    if (order != other.order) return false

    return true
  }

  override fun hashCode(): Int {
    return order.hashCode()
  }
}

private class GradleBuildFinishedModelFetchPhase(
  override val order: Int,
  override val name: String,
) : GradleModelFetchPhase.BuildFinished {

  override fun toString(): String = name

  override fun compareTo(other: GradleModelFetchPhase): Int {
    return when (other) {
      is GradleBaseScriptModelFetchPhase -> 1
      is GradleProjectLoadedModelFetchPhase -> 1
      is GradleBuildFinishedModelFetchPhase -> order.compareTo(other.order)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleBuildFinishedModelFetchPhase) return false

    if (order != other.order) return false

    return true
  }

  override fun hashCode(): Int {
    return order.hashCode()
  }
}