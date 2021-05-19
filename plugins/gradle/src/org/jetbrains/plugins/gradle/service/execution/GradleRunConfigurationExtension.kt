// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemReifiedRunConfigurationExtension
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemTasksAndArgumentsField.TasksAndArgumentsInfo
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemTasksAndArgumentsField.TasksAndArgumentsInfo.ArgumentInfo
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemTasksAndArgumentsField.TasksAndArgumentsInfo.TaskInfo
import com.intellij.openapi.project.Project
import org.apache.commons.cli.Option
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.*

class GradleRunConfigurationExtension
  : ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

  override fun MutableList<SettingsEditorFragment<GradleRunConfiguration, *>>.configureFragments(configuration: GradleRunConfiguration) {
    val project = configuration.project
    val projectPathSettings = createProjectPath<GradleRunConfiguration>(project, GradleConstants.SYSTEM_ID)
    val tasksAndArgumentsInfo = GradleTasksAndArgumentsInfo(project, projectPathSettings.component().component)
    val tasksAndArgumentsSettings = createTasksAndArguments<GradleRunConfiguration>(project, tasksAndArgumentsInfo)

    add(createBeforeRun(GradleBeforeRunTaskProvider.ID))
    add(projectPathSettings)
    add(tasksAndArgumentsSettings)
    add(createScriptDebugEnabledTag())
    add(createReattachDebugProcessTag())
    add(createDebugAllEnabledTag())
  }

  private fun createScriptDebugEnabledTag() = createSettingsTag(
    "gradle.tasks.script.debugging.fragment",
    GradleBundle.message("gradle.tasks.script.debugging"),
    GradleBundle.message("gradle.settings.title.debug"),
    null,
    GradleRunConfiguration::isScriptDebugEnabled,
    GradleRunConfiguration::setScriptDebugEnabled,
    200
  )

  private fun createReattachDebugProcessTag() = createSettingsTag(
    "gradle.tasks.reattach.debug.process.fragment",
    GradleBundle.message("gradle.tasks.reattach.debug.process"),
    GradleBundle.message("gradle.settings.title.debug"),
    GradleBundle.message("gradle.tasks.reattach.debug.process.comment"),
    GradleRunConfiguration::isReattachDebugProcess,
    GradleRunConfiguration::setReattachDebugProcess,
    200
  )

  private fun createDebugAllEnabledTag() = createSettingsTag(
    "gradle.tasks.debugging.all.fragment",
    GradleBundle.message("gradle.tasks.debugging.all"),
    GradleBundle.message("gradle.settings.title.debug"),
    GradleBundle.message("gradle.tasks.debugging.all.comment"),
    GradleRunConfiguration::isDebugAllEnabled,
    GradleRunConfiguration::setDebugAllEnabled,
    200
  )

  private class GradleTasksAndArgumentsInfo(
    private val project: Project,
    private val projectPathField: ExternalSystemProjectPathField
  ) : TasksAndArgumentsInfo {

    override val tasks: List<TaskInfo>
      get() {
        val allTasks = getGradleTasks(project)
        val projectPath = projectPathField.projectPath
        val moduleNode = GradleUtil.findGradleModuleData(project, projectPath) ?: return emptyList()
        val gradlePath = GradleProjectResolverUtil.getGradlePath(moduleNode.data)
          .removeSuffix(":")
        val tasks = allTasks[projectPath] ?: return emptyList()
        val wildcardTasksInfo = ArrayList<TaskInfo>()
        val tasksInfo = ArrayList<TaskInfo>()
        for ((path, tasksData) in tasks.entrySet()) {
          for (taskData in tasksData) {
            val taskFqn = getGradleFqnTaskName(path, taskData)
              .removePrefix(gradlePath)
            val taskDescription = taskData.description
            wildcardTasksInfo.add(TaskInfo(taskFqn.removePrefix(":"), taskDescription))
            if (!taskData.isInherited) {
              tasksInfo.add(TaskInfo(taskFqn, taskDescription))
            }
          }
        }
        return wildcardTasksInfo.sortedBy { it.name } +
               tasksInfo.sortedBy { it.name }
      }

    override val arguments = GradleCommandLineOptionsProvider.getSupportedOptions().options
      .filterIsInstance<Option>()
      .mapNotNull {
        val longOpt = it.longOpt
        val shortOpt = it.opt
        when {
          longOpt != null && shortOpt != null -> ArgumentInfo("--$longOpt", "-$shortOpt", it.description)
          longOpt != null -> ArgumentInfo("--$longOpt", null, it.description)
          shortOpt != null -> ArgumentInfo("-$shortOpt", null, it.description)
          else -> null
        }
      }.sortedBy { it.name }
  }
}
