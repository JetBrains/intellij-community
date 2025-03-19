// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * Extension point to fine-tune Gradle Tooling API calls.
 *
 * E.g. a client may add GradleEventListeners to collect specific information or statistics.
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
   * I.e. For Gradle sync and Gradle task executions.**
   *
   * **Note: This function may be called more than once with different [operation]s for a single Gradle project sync.**
   */
  fun prepareForExecution(
    id: ExternalSystemTaskId,
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    buildEnvironment: BuildEnvironment?,
  ) = Unit
}