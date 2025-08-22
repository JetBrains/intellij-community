// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic.Companion.asSyncPhase

@ApiStatus.Experimental
@ApiStatus.NonExtendable
sealed interface GradleSyncPhase : Comparable<GradleSyncPhase> {

  /**
   * This name is used for the Gradle model fetch identification.
   * For example, in open telemetry and IntelliJ logs.
   */
  val name: String

  /**
   * In these phases, Gradle sync contributors are executed when the IDE has not yet started execution on the Gradle daemon side.
   */
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
  sealed interface Dynamic : GradleSyncPhase {

    val modelFetchPhase: GradleModelFetchPhase

    companion object {

      fun GradleModelFetchPhase.asSyncPhase(): GradleSyncPhase {
        return GradleDynamicSyncPhase(this)
      }
    }
  }

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
     * In this phase, Gradle sync contributors,
     * contribute to the IDE project model for a rich experience in IntelliJ IDEA.
     * It is a code insight in Gradle scripts, data for run configuration creation and code completion in him,
     * data for code profiling, etc.
     */
    @JvmField
    val ADDITIONAL_MODEL_PHASE: GradleSyncPhase = GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE.asSyncPhase()
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
      is GradleDynamicSyncPhase -> -1
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