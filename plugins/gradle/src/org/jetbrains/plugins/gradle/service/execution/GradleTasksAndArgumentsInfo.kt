// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsInfo
import com.intellij.openapi.project.Project
import org.apache.commons.cli.Option
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.getGradleFqnTaskName
import org.jetbrains.plugins.gradle.util.getGradleTasks
import javax.swing.Icon

class GradleTasksAndArgumentsInfo(
  project: Project,
  projectPathField: ExternalSystemProjectPathField
) : ExternalSystemTasksAndArgumentsInfo {
  override val hint: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.hint")
  override val title: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.title")
  override val tooltip: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.tooltip")
  override val emptyState: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.empty.state")
  override val name: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.name")

  override val tablesInfo: List<ExternalSystemTasksAndArgumentsInfo.CompletionTableInfo> =
    listOf(TasksCompletionTableInfo(project, projectPathField), ArgumentsCompletionTableInfo())

  private class TasksCompletionTableInfo(
    private val project: Project,
    private val projectPathField: ExternalSystemProjectPathField
  ) : ExternalSystemTasksAndArgumentsInfo.CompletionTableInfo {
    override val emptyState: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.tasks.empty.text")
    override val dataIcon: Icon = AllIcons.General.Gear
    override val dataColumnName: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.task.column")
    override val descriptionColumnName: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.description.column")

    override val completionInfo: List<TextCompletionContributor.TextCompletionInfo> by lazy {
      val allTasks = getGradleTasks(project)
      val projectPath = projectPathField.projectPath
      val moduleNode = GradleUtil.findGradleModuleData(project, projectPath) ?: return@lazy emptyList()
      val gradlePath = GradleProjectResolverUtil.getGradlePath(moduleNode.data)
        .removeSuffix(":")
      val tasks = allTasks[projectPath] ?: return@lazy emptyList()
      val wildcardTasksInfo = ArrayList<TextCompletionContributor.TextCompletionInfo>()
      val tasksInfo = ArrayList<TextCompletionContributor.TextCompletionInfo>()
      for ((path, tasksData) in tasks.entrySet()) {
        for (taskData in tasksData) {
          val taskFqn = getGradleFqnTaskName(path, taskData)
            .removePrefix(gradlePath)
          val taskDescription = taskData.description
          wildcardTasksInfo.add(TextCompletionContributor.TextCompletionInfo(taskFqn.removePrefix(":"), taskDescription))
          if (!taskData.isInherited) {
            tasksInfo.add(TextCompletionContributor.TextCompletionInfo(taskFqn, taskDescription))
          }
        }
      }
      wildcardTasksInfo.sortedBy { it.text } + tasksInfo.sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionContributor.TextCompletionInfo> by lazy { completionInfo }
  }

  private class ArgumentsCompletionTableInfo : ExternalSystemTasksAndArgumentsInfo.CompletionTableInfo {
    override val emptyState: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.arguments.empty.text")
    override val dataIcon: Icon? = null
    override val dataColumnName: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.argument.column")
    override val descriptionColumnName: String = GradleBundle.message("gradle.run.configuration.tasks.and.arguments.description.column")

    private val options: List<Option> =
      GradleCommandLineOptionsProvider.getSupportedOptions().options
        .filterIsInstance<Option>()

    override val completionInfo: List<TextCompletionContributor.TextCompletionInfo> by lazy {
      options
        .filter { it.opt != null }
        .map { TextCompletionContributor.TextCompletionInfo("-" + it.opt, it.description) }
        .sortedBy { it.text } +
      options
        .filter { it.longOpt != null }
        .map { TextCompletionContributor.TextCompletionInfo("--" + it.longOpt, it.description) }
        .sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionContributor.TextCompletionInfo> by lazy {
      completionInfo.filter { it.text.startsWith("--") }
    }
  }
}