// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator
import com.intellij.openapi.externalSystem.service.project.settings.BeforeRunTaskImporter
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

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


class GradleTaskTriggersImporter : ConfigurationHandler {
  override fun apply(project: Project,
                     modelsProvider: IdeModifiableModelsProvider,
                     configuration: ConfigurationData) {
    val obj = configuration.find("taskTriggersConfig") as? Map<*, *> ?: return

    val taskTriggerConfig = obj as Map<String, Collection<*>>
    val activator = ExternalProjectsManagerImpl.getInstance(project).taskActivator
    taskTriggerConfig.forEach { phaseName, tasks ->
      val phase = PHASE_MAP[phaseName] ?: return@forEach
      (tasks as Collection<Map<*, *>>).forEach { taskInfo ->
        val projectPath = taskInfo["projectPath"]
        val taskPath = taskInfo["taskPath"]
        if (projectPath is String && taskPath is String) {
          activator.addTask(ExternalSystemTaskActivator.TaskActivationEntry(GradleConstants.SYSTEM_ID,
                                                                            phase,
                                                                            projectPath,
                                                                            taskPath))
        }
      }
    }
  }

  companion object {
    private val PHASE_MAP = mapOf("beforeSync" to ExternalSystemTaskActivator.Phase.BEFORE_SYNC,
                                  "afterSync" to ExternalSystemTaskActivator.Phase.AFTER_SYNC,
                                  "beforeBuild" to ExternalSystemTaskActivator.Phase.BEFORE_COMPILE,
                                  "afterBuild" to ExternalSystemTaskActivator.Phase.AFTER_COMPILE,
                                  "beforeRebuild" to ExternalSystemTaskActivator.Phase.BEFORE_REBUILD,
                                  "afterRebuild" to ExternalSystemTaskActivator.Phase.AFTER_REBUILD)
  }
}

class ActionDelegateConfigImporter: ConfigurationHandler {
  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val config = configuration.find("actionDelegationConfig") as? Map<String, *> ?: return
    val settings = GradleSystemRunningSettings.getInstance()

    consumeIfCast(config["delegateBuildRunToGradle"], java.lang.Boolean::class.java) { settings.isUseGradleAwareMake = it.booleanValue() }
    consumeIfCast(config["testRunner"], String::class.java) {
      settings.preferredTestRunner = TEST_RUNNER_MAP[it] ?: return@consumeIfCast
    }
  }

  companion object {
    private val TEST_RUNNER_MAP = mapOf(
      "PLATFORM" to GradleSystemRunningSettings.PreferredTestRunner.PLATFORM_TEST_RUNNER,
      "GRADLE" to GradleSystemRunningSettings.PreferredTestRunner.GRADLE_TEST_RUNNER,
      "CHOOSE_PER_TEST" to GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST
    )
  }
}