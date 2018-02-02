// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.BeforeRunTaskImporter
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider

class GradleBeforeRunTaskImporter: BeforeRunTaskImporter {
  override fun process(project: Project,
                       modelsProvider: IdeModifiableModelsProvider,
                       runConfiguration: RunConfiguration,
                       beforeRunTasks: MutableList<BeforeRunTask<*>>,
                       cfg: MutableMap<String, Any>): MutableList<BeforeRunTask<*>> {

    val taskProvider = BeforeRunTaskProvider.getProvider(project, GradleBeforeRunTaskProvider.ID) ?: return beforeRunTasks
    val task = taskProvider.createTask(runConfiguration) ?: return beforeRunTasks
    task.taskExecutionSettings.apply {
      consumeIfCast(cfg["taskName"], String::class.java) { taskNames = listOf(it) }
      consumeIfCast(cfg["projectPath"], String::class.java) { externalProjectPath = it }
    }
    task.isEnabled = true
    beforeRunTasks.add(task)
    return beforeRunTasks
  }

  override fun canImport(typeName: String): Boolean = "gradleTask" == typeName
}