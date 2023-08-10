// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.util.createTextModificationTracker
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.apache.commons.cli.Option
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices
import org.jetbrains.plugins.gradle.util.GradleBundle
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

    override val completionModificationTracker: ModificationTracker =
      workingDirectoryField.createTextModificationTracker()

    override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
      return blockingContext {
        val indices = GradleTasksIndices.getInstance(project)
        indices.getTasksCompletionVariances(workingDirectoryField.workingDirectory)
          .map { TextCompletionInfo(it.key, it.value.first().description) }
          .sortedWith(Comparator.comparing({ it.text }, GRADLE_COMPLETION_COMPARATOR))
      }
    }

    override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
      return blockingContext {
        val indices = GradleTasksIndices.getInstance(project)
        indices.findTasks(workingDirectoryField.workingDirectory)
          .filterNot { it.isInherited }
          .groupBy { it.name }
          .map { TextCompletionInfo(it.key, it.value.first().description) }
          .sortedWith(Comparator.comparing({ it.text }, GRADLE_COMPLETION_COMPARATOR))
      }
    }
  }

  private class ArgumentsCompletionTableInfo : CompletionTableInfo {
    override val emptyState: String = GradleBundle.message("gradle.run.configuration.command.line.arguments.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.argument.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = GradleBundle.message("gradle.run.configuration.command.line.description.column")

    private suspend fun collectOptionCompletion(isLongOption: Boolean): List<TextCompletionInfo> {
      return blockingContext {
        val prefix = if (isLongOption) "--" else "-"
        fun Option.getName(): String? = if (isLongOption) longOpt else opt
        GradleCommandLineOptionsProvider.getSupportedOptions().options
          .filterIsInstance<Option>()
          .filter { it.getName() != null }
          .map { TextCompletionInfo(prefix + it.getName(), it.description) }
          .sortedBy { it.text }
      }
    }

    override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
      return collectOptionCompletion(isLongOption = false) +
             collectOptionCompletion(isLongOption = true)
    }

    override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
      return collectOptionCompletion(isLongOption = true)
    }
  }

  companion object {

    val GRADLE_COMPLETION_COMPARATOR = Comparator<String> { o1, o2 ->
      when {
        o1.startsWith("--") && o2.startsWith("--") -> o1.compareTo(o2)
        o1.startsWith("-") && o2.startsWith("--") -> -1
        o1.startsWith("--") && o2.startsWith("-") -> 1
        o1.startsWith(":") && o2.startsWith(":") -> o1.compareTo(o2)
        o1.startsWith(":") && o2.startsWith("-") -> -1
        o1.startsWith("-") && o2.startsWith(":") -> 1
        o2.startsWith("-") -> -1
        o2.startsWith(":") -> -1
        o1.startsWith("-") -> 1
        o1.startsWith(":") -> 1
        else -> o1.compareTo(o2)
      }
    }
  }
}