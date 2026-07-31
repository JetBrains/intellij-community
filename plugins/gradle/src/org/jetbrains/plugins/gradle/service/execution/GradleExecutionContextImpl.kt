// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@Internal
open class GradleExecutionContextImpl(
  override val projectPath: String,
  override val taskId: ExternalSystemTaskId,
  override val settings: GradleExecutionSettings,
  override val listener: ExternalSystemTaskNotificationListener,
  override val cancellationToken: CancellationToken,
) : GradleExecutionContext, UserDataHolderBase() {

  override val project: Project by taskId::project

  private var _buildEnvironment: BuildEnvironment? = null
  override var buildEnvironment: BuildEnvironment
    get() = checkNotNull(_buildEnvironment) {
      "The Gradle Daemon connection wasn't acquired for $taskId.\n" +
      "See [org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper#execute] from more details."
    }
    set(value) {
      _buildEnvironment = value
      _reporter.buildEnvironment = value
    }

  override val gradleVersion: GradleVersion
    get() = GradleVersion.version(buildEnvironment.gradle.gradleVersion)

  private var _reporter: GradleExecutionReporterImpl = GradleExecutionReporterImpl(projectPath, taskId, listener)
  override val reporter: GradleExecutionReporterImpl by ::_reporter

  constructor(context: GradleExecutionContextImpl) :
    this(context, context.projectPath, GradleExecutionSettings(context.settings))

  constructor(
    context: GradleExecutionContextImpl,
    projectPath: String,
    settings: GradleExecutionSettings,
  ) : this(
    projectPath, context.taskId, settings, context.listener, context.cancellationToken
  ) {
    context.copyUserDataTo(this)
    this._reporter = context._reporter
    this._buildEnvironment = context._buildEnvironment
  }
}