// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext

/**
 * The context of a whole Gradle task execution.
 *
 * [GradleTaskManager] uses this context, and it can span several [GradleExecutionContext] operations.
 * For example, [GradleTaskManager] can install the Gradle wrapper in one operation, then run the requested tasks in another.
 * [GradleTaskManagerExtension] receives this context when it configures or overrides task execution.
 */
@NonExtendable
interface GradleTaskExecutionContext {

  /**
   * The IDE project that owns this Gradle operation.
   */
  val project: Project

  /**
   * Path of the current Gradle operation scope, usually the linked Gradle project path.
   *
   * This is not necessarily the IDE project path (which may contain multiple linked Gradle projects).
   */
  val projectPath: String

  /**
   * The ID of the external system task that runs this execution.
   */
  val taskId: ExternalSystemTaskId

  /**
   * The listener that receives notifications about this task execution.
   */
  val listener: ExternalSystemTaskNotificationListener

  /**
   * The context of the current Gradle operation within this task execution.
   *
   * A caller can read this property only after this task execution starts a Gradle operation.
   */
  val executionContext: GradleExecutionContext
}
