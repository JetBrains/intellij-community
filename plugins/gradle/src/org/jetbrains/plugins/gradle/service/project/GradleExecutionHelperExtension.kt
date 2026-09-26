// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * Defines extension with low-level and high-level Gradle execution parameters configurators.
 *
 * The configurator with [GradleExecutionSettings] is preferred to be used instead of [LongRunningOperation].
 * Because it protects extensions from conflicts due to the parameters replacement API in [LongRunningOperation].
 */
interface GradleExecutionHelperExtension {

  /**
   * Prepare a Gradle [settings] before any Gradle execution.
   *
   * **Note: This function will be called for any Gradle execution.
   * I.e., for Gradle sync and Gradle task executions.**
   *
   * **Note: This function may be called more than once with different [settings]s for a single Gradle project sync.**
   */
  fun configureSettings(settings: GradleExecutionSettings, context: GradleExecutionContext): Unit = Unit

  /**
   * Prepare a low-level Gradle [operation] before any Gradle execution.
   *
   * Consider using the high-level [configureSettings] function instead.
   * The [GradleExecutionSettings] provides mode flexibility for the defining and arranging CLI arguments and VM options.
   * The [LongRunningOperation] provides only unsafe replacement API, that may replace arguments from settings and other extensions.
   *
   * @see <a href="https://docs.gradle.org/current/userguide/tooling_api.html">Gradle tooling API</a>
   */
  fun configureOperation(operation: LongRunningOperation, context: GradleExecutionContext) {
    prepareForExecution(context.taskId, operation, context.settings, context.buildEnvironment)
  }

  @Deprecated("Use [configureSettings] or [configureOperation] instead")
  fun prepareForExecution(
    id: ExternalSystemTaskId,
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    buildEnvironment: BuildEnvironment?,
  ): Unit = Unit

  companion object {

    @JvmField
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<GradleExecutionHelperExtension> = ExtensionPointName.create("org.jetbrains.plugins.gradle.executionHelperExtension")
  }
}