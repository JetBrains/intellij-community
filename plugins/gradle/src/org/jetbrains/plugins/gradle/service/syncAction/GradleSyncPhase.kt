// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic.Companion.asSyncPhase

/**
 * Represents a phase of Gradle sync during which [GradleSyncContributor][org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor]s
 * contribute entities to the workspace model.
 *
 * Phases are ordered and grouped into distinct classes ([Static], [Dynamic], [DataServices]).
 * Each phase class produces a complete, self-contained project model. Models from different
 * phase classes conflict with each other, so later classes take priority over earlier ones:
 * a later class replaces the project model produced by all preceding classes entirely.
 * Earlier classes are faster but less accurate, later classes are slower but more precise.
 *
 * To simplify this "replace entirely" semantics, the sync storage accumulates contributed
 * entities within one phase class and is reset when transitioning to the next class.
 *
 * A phase is executed only when at least one [GradleSyncContributor] is registered for it.
 * [org.jetbrains.plugins.gradle.service.syncAction.impl.GradleSyncProjectConfigurator] is the only
 * component that owns this guarantee; declaring a phase constant does not schedule it by itself.
 */
@Experimental
@NonExtendable
sealed interface GradleSyncPhase : Comparable<GradleSyncPhase> {

  /**
   * This name is used for the Gradle sync phase identification.
   * For example, in open telemetry and IntelliJ logs.
   */
  val name: String

  /**
   * In these phases, Gradle sync contributors are executed when the IDE has not yet started execution on the Gradle daemon side.
   */
  @NonExtendable
  sealed interface Static : GradleSyncPhase {

    val order: Int

    companion object {

      operator fun invoke(order: Int, name: String): Static {
        return GradleStaticSyncPhase(order, name)
      }
    }
  }

  /**
   * In these phases, Gradle sync contributors are executed when models for [modelFetchPhase] are collected on the Gradle daemon side.
   */
  @NonExtendable
  sealed interface Dynamic : GradleSyncPhase {

    val modelFetchPhase: GradleModelFetchPhase

    companion object {

      fun GradleModelFetchPhase.asSyncPhase(): GradleSyncPhase {
        return GradleDynamicSyncPhase(this)
      }
    }
  }

  /**
   * The phase corresponding to IntelliJ Platform data services execution.
   * This is a temporary, internal API for migration purposes.
   */
  @Internal
  @NonExtendable
  sealed interface DataServices : GradleSyncPhase

  companion object {

    /**
     * In this phase, Gradle sync contributors,
     * contribute to the IDE project model based on the Gradle sync parameters.
     */
    @JvmField
    val INITIAL_PHASE: GradleSyncPhase = Static(0, "INITIAL_PHASE")

    /**
     * In this phase, Gradle sync contributors,
     * contribute to the IDE project model based on the static Gradle DCL model.
     */
    @JvmField
    val DECLARATIVE_PHASE: GradleSyncPhase = Static(1000, "DECLARATIVE_PHASE")

    /**
     * In this phase, Gradle sync contributors provide a base IDE model for Gradle script files
     * using a lightweight classpath model available before the Gradle daemon runs any tasks.
     * This enables basic IDE support for open script files early in sync.
     */
    @JvmField
    @Internal
    val BASE_SCRIPT_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.BASE_SCRIPT_MODEL_PHASE.asSyncPhase()

    /**
     * In this phase, Gradle sync contributors,
     * contribute to the IDE module and content root structure for each Gradle project.
     */
    @JvmField
    val PROJECT_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.PROJECT_MODEL_PHASE.asSyncPhase()

    /**
     * In this phase, Gradle sync contributors,
     * contribute the IDE module, content root and source folder structure for each Gradle source set.
     */
    @JvmField
    val SOURCE_SET_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE.asSyncPhase()

    /**
     * In this phase, Gradle sync contributors,
     * contribute to the IDE module dependency structure for each Gradle source set.
     */
    @JvmField
    val DEPENDENCY_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE.asSyncPhase()

    /**
     * In this phase, Gradle sync contributors contribute the full IDE model for Gradle
     * script files, collected from the Gradle daemon after all build tasks have run.
     *
     * This replaces the base model written in [BASE_SCRIPT_MODEL_PHASE] with accurate data.
     */
    @JvmField
    val SCRIPT_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.SCRIPT_MODEL_PHASE.asSyncPhase()

    /**
     * In this phase, Gradle sync contributors,
     * contribute to the IDE project model for a rich experience in IntelliJ IDEA.
     * It is a code insight in Gradle scripts, data for run configuration creation and code completion in him,
     * data for code profiling, etc.
     */
    @JvmField
    val ADDITIONAL_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE.asSyncPhase()

    @JvmField
    val DATA_SERVICES_PHASE: GradleSyncPhase = GradleDataServicesSyncPhase()
  }
}

// Implementation

private class GradleStaticSyncPhase(
  override val order: Int,
  override val name: String,
) : GradleSyncPhase.Static {

  override fun toString(): String = name

  override fun compareTo(other: GradleSyncPhase): Int {
    return when (other) {
      is GradleStaticSyncPhase -> order.compareTo(other.order)
      is GradleDynamicSyncPhase,
      is GradleDataServicesSyncPhase -> -1
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleStaticSyncPhase) return false

    if (order != other.order) return false

    return true
  }

  override fun hashCode(): Int {
    return order.hashCode()
  }
}

private class GradleDynamicSyncPhase(
  override val modelFetchPhase: GradleModelFetchPhase,
) : GradleSyncPhase.Dynamic {

  override val name: String by modelFetchPhase::name

  override fun toString(): String = name

  override fun compareTo(other: GradleSyncPhase): Int {
    return when (other) {
      is GradleStaticSyncPhase -> 1
      is GradleDynamicSyncPhase -> modelFetchPhase.compareTo(other.modelFetchPhase)
      is GradleDataServicesSyncPhase -> -1
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleDynamicSyncPhase) return false

    if (modelFetchPhase != other.modelFetchPhase) return false

    return true
  }

  override fun hashCode(): Int {
    return modelFetchPhase.hashCode()
  }
}

private class GradleDataServicesSyncPhase : GradleSyncPhase.DataServices {

  override val name: String = "DATA_SERVICES"

  override fun compareTo(other: GradleSyncPhase): Int =
    if (other is GradleDataServicesSyncPhase) 0 else 1
}