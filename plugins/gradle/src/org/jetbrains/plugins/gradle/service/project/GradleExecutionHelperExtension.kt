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
   * Prepare an operation call during Gradle sync.
   * May be called more than once with different operations for a single Gradle project refresh.
   */
  fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext)

  /**
   * Prepare an operation call before Gradle task execution
   */
  fun prepareForExecution(id: ExternalSystemTaskId,
                          operation: LongRunningOperation,
                          gradleExecutionSettings: GradleExecutionSettings,
                          buildEnvironment: BuildEnvironment?)
}