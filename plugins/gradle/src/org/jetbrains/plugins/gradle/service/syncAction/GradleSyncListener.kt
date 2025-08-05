// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@ApiStatus.Internal
interface GradleSyncListener {

  /**
   * Called when Gradle sync phase is completed.
   *
   * @param context contain all information about the current state of the Gradle sync.
   * @param phase current phase of the model fetching action.
   */
  fun onSyncPhaseCompleted(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ): Unit = Unit

  /**
   * Called once Gradle has finished executing everything, including any tasks that might need to be run.
   * The models are obtained separately and in some cases before this method is called.
   *
   * @param context contain all information about the current state of the Gradle sync.
   */
  fun onModelFetchCompleted(
    context: ProjectResolverContext,
  ): Unit = Unit

  /**
   * Called once Gradle has failed to execute everything.
   * The models are obtained separately and in some cases before this method is called.
   *
   * @param context contain all information about the current state of the Gradle sync.
   * @param exception the exception thrown by Gradle, if everything completes successfully, then this will be null.
   */
  fun onModelFetchFailed(
    context: ProjectResolverContext,
    exception: Throwable,
  ): Unit = Unit

  /**
   * Called once Gradle has loaded projects but before any task execution.
   * These models do not contain those models that are created when the build finished.
   *
   * @param context contain all information about the current state of the Gradle sync.
   */
  fun onProjectLoadedActionCompleted(
    context: ProjectResolverContext,
  ): Unit = Unit

  companion object {

    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<GradleSyncListener> = Topic(GradleSyncListener::class.java)
  }
}