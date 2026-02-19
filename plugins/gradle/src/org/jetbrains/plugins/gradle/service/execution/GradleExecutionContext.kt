// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@ApiStatus.NonExtendable
interface GradleExecutionContext: UserDataHolderEx {

  val project: Project

  val projectPath: String

  val taskId: ExternalSystemTaskId

  val settings: GradleExecutionSettings

  val listener: ExternalSystemTaskNotificationListener

  val cancellationToken: CancellationToken

  val buildEnvironment: BuildEnvironment

  val gradleVersion: GradleVersion
}