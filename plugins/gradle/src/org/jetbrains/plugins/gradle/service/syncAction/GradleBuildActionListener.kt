// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.GradleConnectionException
import java.util.*

interface GradleBuildActionListener : EventListener {

  /**
   * Called when Gradle model building phase is completed.
   * Guaranteed that all phases will be handled for the successful execution in the strict order.
   *
   * Note: This method is called from a Gradle connection thread, within the [org.gradle.tooling.StreamedValueListener] passed to the
   * tooling api.
   */
  fun onPhaseCompleted(phase: GradleModelFetchPhase) {}

  /**
   * Called once Gradle has loaded projects but before any task execution.
   * These models do not contain those models that are created when the build finished.
   *
   * Note: This method is called from a Gradle connection thread, within the [org.gradle.tooling.IntermediateResultHandler] passed to the
   * tooling api.
   */
  fun onProjectLoaded() {}

  /**
   * Called once Gradle has finished executing everything, including any tasks that might need to be run.
   * The models are obtained separately and in some cases before this method is called.
   *
   * Note: This method is called from a Gradle connection thread, within the [org.gradle.tooling.ResultHandler] passed to the
   * tooling api.
   */
  fun onBuildCompleted() {}

  /**
   * Called once Gradle has failed to execute everything.
   * The models are obtained separately and in some cases before this method is called.
   *
   * @param exception the exception thrown by Gradle, if everything completes successfully, then this will be null.
   *
   * Note: This method is called from a Gradle connection thread, within the [org.gradle.tooling.ResultHandler] passed to the
   * tooling api.
   */
  fun onBuildFailed(exception: GradleConnectionException) {}
}