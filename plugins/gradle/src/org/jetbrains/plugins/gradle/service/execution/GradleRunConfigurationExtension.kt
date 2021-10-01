// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemReifiedRunConfigurationExtension
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArguments
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArguments.Argument
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArguments.Task
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
    val tasksAndArguments = GradleTasksAndArguments(project, projectPathSettings.component().component)
    val tasksAndArgumentsSettings = createTasksAndArguments<GradleRunConfiguration>(project, GradleConstants.SYSTEM_ID, tasksAndArguments)

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

  private class GradleTasksAndArguments(
    private val project: Project,
    private val projectPathField: ExternalSystemProjectPathField
  ) : ExternalSystemTasksAndArguments {

    override val tasks: List<Task>
      get() {
        val allTasks = getGradleTasks(project)
        val projectPath = projectPathField.projectPath
        val moduleNode = GradleUtil.findGradleModuleData(project, projectPath) ?: return emptyList()
        val gradlePath = GradleProjectResolverUtil.getGradlePath(moduleNode.data)
          .removeSuffix(":")
        val tasks = allTasks[projectPath] ?: return emptyList()
        val wildcardTasksInfo = ArrayList<Task>()
        val tasksInfo = ArrayList<Task>()
        for ((_, tasksData) in tasks.entrySet()) {
          for (taskData in tasksData) {
            val taskFqn = taskData.getFqnTaskName().removePrefix(gradlePath)
            val taskDescription = taskData.description
            if (!taskData.isFromIncludedBuild) {
              wildcardTasksInfo.add(Task(taskFqn.removePrefix(":"), taskDescription))
            }
            if (!taskData.isInherited) {
              tasksInfo.add(Task(taskFqn, taskDescription))
            }
          }
        }
        return wildcardTasksInfo.sortedBy { it.name } +
               tasksInfo.sortedBy { it.name }
      }

    override val arguments: List<Argument>
      get() = GradleCommandLineOptionsProvider.getSupportedOptions().options
        .filterIsInstance<Option>()
        .mapNotNull {
          val longOpt = it.longOpt
          val shortOpt = it.opt
          when {
            longOpt != null && shortOpt != null -> Argument("--$longOpt", "-$shortOpt", it.description)
            longOpt != null -> Argument("--$longOpt", null, it.description)
            shortOpt != null -> Argument("-$shortOpt", null, it.description)
            else -> null
          }
        }.sortedBy { it.name }
  }
}
