// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.tasks.TaskManager

internal class TaskManagerStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    (TaskManager.getManager(project) as TaskManagerImpl).projectOpened()
  }
}
