// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@ApiStatus.Internal
open class GradleExecutionContextImpl(
  override val projectPath: String,
  override val taskId: ExternalSystemTaskId,
  override val settings: GradleExecutionSettings,
  override val listener: ExternalSystemTaskNotificationListener,
  override val cancellationToken: CancellationToken,
) : GradleExecutionContext, UserDataHolderBase() {

  override val project: Project
    get() = taskId.project

  private var _buildEnvironment: BuildEnvironment? = null
  override var buildEnvironment: BuildEnvironment
    get() = checkNotNull(_buildEnvironment) {
      "The Gradle Daemon connection wasn't acquired for $taskId.\n" +
      "See [org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper#execute] from more details."
    }
    set(value) {
      _buildEnvironment = value
    }

  override val gradleVersion: GradleVersion
    get() = GradleVersion.version(buildEnvironment.gradle.gradleVersion)

  constructor(
    context: GradleExecutionContext,
    projectPath: String,
    settings: GradleExecutionSettings,
  ) : this(
    projectPath, context.taskId, settings, context.listener, context.cancellationToken
  )
}