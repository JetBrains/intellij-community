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

/**
 * The context of one Gradle execution, such as fetching a model or running a build.
 *
 * [GradleExecutionHelper] uses this context to run the operation.
 */
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

  /**
   * The [com.intellij.openapi.externalSystem.model.task.ExternalSystemTask]'s ID that runs this operation.
   */
  val taskId: ExternalSystemTaskId

  /**
   * The settings for this Gradle execution.
   */
  val settings: GradleExecutionSettings

  /**
   * The listener that receives notifications about this operation.
   */
  val listener: ExternalSystemTaskNotificationListener

  /**
   * The token that cancels this operation.
   */
  val cancellationToken: CancellationToken

  /**
   * The build environment of the connected Gradle daemon.
   *
   * A caller can read this property only after this operation connects to the daemon.
   */
  val buildEnvironment: BuildEnvironment

  /**
   * The Gradle version of the connected Gradle daemon.
   */
  val gradleVersion: GradleVersion

  /**
   * Reports the progress of this operation.
   */
  @get:Experimental
  val reporter: GradleExecutionReporter
}
