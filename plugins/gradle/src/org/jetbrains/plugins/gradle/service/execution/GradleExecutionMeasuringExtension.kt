// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * This extension is obsolete and has already been removed in 233. Metrics collected by extensions are no longer needed in 232.
 * The reason for removing logic from this extension is to prevent performance degradation (IDEA-331512) introduced in IDEA-323893.
 *
 * The remaining code is kept in the repository to prevent possible bugs, as the amount of affected code is quite large.
 */
class GradleExecutionMeasuringExtension : GradleOperationHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings,
                                   buildEnvironment: BuildEnvironment?) = Unit

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) = Unit

}
