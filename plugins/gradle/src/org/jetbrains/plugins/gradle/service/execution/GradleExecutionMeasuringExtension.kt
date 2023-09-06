// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.registry.Registry
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleOlderThan
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleExecutionStageFusHandler
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.lang.ref.WeakReference

class GradleExecutionMeasuringExtension : GradleOperationHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings,
                                   buildEnvironment: BuildEnvironment?) {
    if (isSkipExecution(id, buildEnvironment)) {
      return
    }
    val handler = GradleExecutionStageFusHandler(id.id, WeakReference(id.findProject()))
    val router = TaskExecutionAggregatedRouter(handler)
    operation.addProgressListener(
      ProgressListener {
        router.route(it)
      },
      OperationType.TASK, OperationType.GENERIC
    )
  }

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) = Unit

  private fun BuildEnvironment.gradleVersion(): GradleVersion? = gradle?.gradleVersion?.let { GradleVersion.version(it) }

  private fun isSkipExecution(id: ExternalSystemTaskId, buildEnvironment: BuildEnvironment?): Boolean {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) {
      return true
    }
    if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT && !Registry.`is`("gradle.import.performance.statistics", true)) {
      return true
    }
    val gradleVersion = buildEnvironment?.gradleVersion()
    if (gradleVersion == null || gradleVersion.isGradleOlderThan("5.1")) {
      return true
    }
    return false
  }
}
