// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.observable.util.whenTextChanged
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

    private val completionInfoProperty = AtomicLazyProperty { calculateCompletionInfo() }

    override val completionInfo by completionInfoProperty
    override val tableCompletionInfo by completionInfoProperty

    private fun calculateCompletionInfo(): List<TextCompletionInfo> {
      val indices = GradleTasksIndices.getInstance(project)
      return indices.getTasksCompletionVariances(workingDirectoryField.workingDirectory)
        .map { TextCompletionInfo(it.key, it.value.first().description) }
    }

    init {
      workingDirectoryField.whenTextChanged {
        completionInfoProperty.reset()
      }
    }
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