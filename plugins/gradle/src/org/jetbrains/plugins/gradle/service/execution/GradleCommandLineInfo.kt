// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.project.Project
import org.apache.commons.cli.Option
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.getGradleTasks
import javax.swing.Icon

class GradleCommandLineInfo(project: Project, workingDirectoryField: WorkingDirectoryField) : CommandLineInfo {
  override val settingsName: String = GradleBundle.message("gradle.run.configuration.command.line.name")
  override val settingsHint: String = GradleBundle.message("gradle.run.configuration.command.line.hint")

  override val dialogTitle: String = GradleBundle.message("gradle.run.configuration.command.line.title")
  override val dialogTooltip: String = GradleBundle.message("gradle.run.configuration.command.line.tooltip")

  override val fieldEmptyState: String = GradleBundle.message("gradle.run.configuration.command.line.empty.state")

  override val tablesInfo: List<CompletionTableInfo> = listOf(
    TasksCompletionTableInfo(project, workingDirectoryField),
    ArgumentsCompletionTableInfo()
  )

  private class TasksCompletionTableInfo(
    private val project: Project,
    private val workingDirectoryField: WorkingDirectoryField
  ) : CompletionTableInfo {
    override val emptyState: String = GradleBundle.message("gradle.run.configuration.command.line.tasks.empty.text")

    override val dataColumnIcon: Icon = AllIcons.General.Gear
    override val dataColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.task.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.description.column")

    override val completionInfo: List<TextCompletionInfo> by lazy {
      val allTasks = getGradleTasks(project)
      val workingDirectory = workingDirectoryField.workingDirectory
      val moduleNode = GradleUtil.findGradleModuleData(project, workingDirectory) ?: return@lazy emptyList()
      val gradlePath = GradleProjectResolverUtil.getGradlePath(moduleNode.data)
        .removeSuffix(":")
      val tasks = allTasks[workingDirectory] ?: return@lazy emptyList()
      val wildcardTasksInfo = ArrayList<TextCompletionInfo>()
      val tasksInfo = ArrayList<TextCompletionInfo>()
      for ((_, tasksData) in tasks.entrySet()) {
        for (taskData in tasksData) {
          val taskFqn = taskData.getFqnTaskName().removePrefix(gradlePath)
          val taskDescription = taskData.description
          if (!taskData.isFromIncludedBuild) {
            wildcardTasksInfo.add(TextCompletionInfo(taskFqn.removePrefix(":"), taskDescription))
          }
          if (!taskData.isInherited) {
            tasksInfo.add(TextCompletionInfo(taskFqn, taskDescription))
          }
        }
      }
      wildcardTasksInfo.sortedBy { it.text } + tasksInfo.sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionInfo> by lazy { completionInfo }
  }

  private class ArgumentsCompletionTableInfo : CompletionTableInfo {
    override val emptyState: String = GradleBundle.message("gradle.run.configuration.command.line.arguments.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.argument.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.description.column")

    private val options: List<Option> =
      GradleCommandLineOptionsProvider.getSupportedOptions().options
        .filterIsInstance<Option>()

    override val completionInfo: List<TextCompletionInfo> by lazy {
      options
        .filter { it.opt != null }
        .map { TextCompletionInfo("-" + it.opt, it.description) }
        .sortedBy { it.text } +
      options
        .filter { it.longOpt != null }
        .map { TextCompletionInfo("--" + it.longOpt, it.description) }
        .sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionInfo> by lazy {
      completionInfo.filter { it.text.startsWith("--") }
    }
  }
}