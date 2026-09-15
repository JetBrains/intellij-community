// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContextImpl

@Internal
class GradleTaskExecutionContextImpl(
  override val projectPath: String,
  override val taskId: ExternalSystemTaskId,
  override val listener: ExternalSystemTaskNotificationListener,
) : GradleTaskExecutionContext {

  override val project: Project by taskId::project

  private var _executionContext: GradleExecutionContextImpl? = null
  override var executionContext: GradleExecutionContextImpl
    get() = checkNotNull(_executionContext) {
      "The Gradle execution context wasn't set for $taskId"
    }
    set(value) {
      _executionContext = value
    }
}
