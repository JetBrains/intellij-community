// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalTask
import org.jetbrains.plugins.gradle.model.GradleTaskModel

@ApiStatus.Internal
class DefaultGradleTaskModel : GradleTaskModel {

  private var tasks = emptyMap<String, DefaultExternalTask>()

  override fun getTasks(): Map<String, DefaultExternalTask> {
    return tasks
  }

  fun setTasks(tasks: Map<String, DefaultExternalTask>) {
    this.tasks = tasks
  }
}