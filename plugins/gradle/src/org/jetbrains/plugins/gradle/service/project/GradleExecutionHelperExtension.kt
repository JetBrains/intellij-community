// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * This is the low-level Gradle execution extension that connects and interacts with the Gradle daemon using the Gradle tooling API.
 *
 * Consider using the high-level Gradle execution extensions instead:
 * * [org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension]
 * * [org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension]
 *
 * @see <a href="https://docs.gradle.org/current/userguide/tooling_api.html">Gradle tooling API</a>
 */
@ApiStatus.Internal
interface GradleExecutionHelperExtension {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleExecutionHelperExtension> = ExtensionPointName.create("org.jetbrains.plugins.gradle.executionHelperExtension")
  }

  /**
   * Prepare a Gradle [operation] before any Gradle execution.
   *
   * **Note: This function will be called for any Gradle execution.
   * I.e., for Gradle sync and Gradle task executions.**
   *
   * **Note: This function may be called more than once with different [operation]s for a single Gradle project sync.**
   */
  fun prepareForExecution(
    id: ExternalSystemTaskId,
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    buildEnvironment: BuildEnvironment?,
  ): Unit = Unit
}