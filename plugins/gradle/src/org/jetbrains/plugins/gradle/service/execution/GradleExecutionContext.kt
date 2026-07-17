// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@NonExtendable
interface GradleExecutionContext: UserDataHolderEx {

  /**
   * The IDE project that owns this Gradle operation.
   */
  val project: Project

  /**
   * Path of the current Gradle operation scope, usually the linked Gradle project path.
   *
   * This is not necessarily the IDE project path (which may contain multiple linked Gradle projects).
   * Also, for Gradle older than 8.0, buildSrc can use a more granular scope than the linked Gradle project.
   */
  val projectPath: String

  val taskId: ExternalSystemTaskId

  val settings: GradleExecutionSettings

  val listener: ExternalSystemTaskNotificationListener

  val cancellationToken: CancellationToken

  val buildEnvironment: BuildEnvironment

  val gradleVersion: GradleVersion

  @get:Experimental
  val reporter: GradleExecutionReporter
}
