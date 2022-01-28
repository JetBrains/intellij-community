// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.Executor
import com.intellij.ide.actions.runAnything.RunAnythingAction.EXECUTOR_KEY
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ModuleContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ProjectContext
import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.getPath
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.substringBeforeLast
import com.intellij.util.indexing.FindSymbolParameters
import icons.GradleIcons
import org.apache.commons.cli.Option
import org.jetbrains.plugins.gradle.action.GradleExecuteTaskAction
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.Icon


class GradleRunAnythingProvider : RunAnythingCommandLineProvider() {
  override fun getIcon(value: String): Icon = GradleIcons.Gradle

  override fun getHelpGroupTitle(): String = SYSTEM_ID.readableName

  override fun getCompletionGroupTitle(): String = GradleBundle.message("popup.title.gradle.tasks")

  override fun getHelpCommandPlaceholder(): String = "gradle <taskName...> <--option-name...>"

  override fun getHelpCommand(): String = HELP_COMMAND

  override fun getHelpCommandAliases(): List<String> = SECONDARY_HELP_COMMANDS

  override fun getHelpIcon(): Icon = GradleIcons.Gradle

  override fun getMainListItem(dataContext: DataContext, value: String) =
    RunAnythingGradleItem(getCommand(value), getIcon(value))

  override fun getExecutionContexts(dataContext: DataContext): List<RunAnythingContext> {
    return super.getExecutionContexts(dataContext).filter {
      it !is ModuleContext || !it.module.isSourceRoot()
    }
  }

  override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
    val project = RunAnythingUtil.fetchProject(dataContext)
    val executionContext = dataContext.getData(EXECUTING_CONTEXT) ?: ProjectContext(project)
    val context = createContext(project, executionContext, dataContext) ?: return emptySequence()
    val (tasksVariants, wildcardTaskVariants) = completeTasks(context)
      .partition { it.startsWith(":") }
      .let { it.first.sorted().asSequence() to it.second.sorted().asSequence() }
    val taskOptionsVariants = completeTaskOptions(commandLine, context).sorted()
    val taskClassArgumentsVariants = completeTaskClassArguments(commandLine, context).sorted()
    val longOptionsVariants = completeOptions(isLongOpt = true).sorted()
    val shortOptionsVariants = completeOptions(isLongOpt = false).sorted()
    return when {
      commandLine.toComplete.startsWith("--") ->
        taskOptionsVariants + longOptionsVariants + shortOptionsVariants + taskClassArgumentsVariants + wildcardTaskVariants + tasksVariants
      commandLine.toComplete.startsWith("-") ->
        taskOptionsVariants + shortOptionsVariants + longOptionsVariants + taskClassArgumentsVariants + wildcardTaskVariants + tasksVariants
      commandLine.toComplete.startsWith(":") ->
        tasksVariants + wildcardTaskVariants + taskOptionsVariants + shortOptionsVariants + longOptionsVariants + taskClassArgumentsVariants
      else ->
        taskClassArgumentsVariants + wildcardTaskVariants + tasksVariants + taskOptionsVariants + longOptionsVariants + shortOptionsVariants
    }
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val project = RunAnythingUtil.fetchProject(dataContext)
    val executionContext = dataContext.getData(EXECUTING_CONTEXT) ?: ProjectContext(project)
    val context = createContext(project, executionContext, dataContext) ?: return false
    GradleExecuteTaskAction.runGradle(project, context.executor, context.workingDirectory, commandLine.command)
    return true
  }

  private fun completeTasks(context: Context): Sequence<String> {
    val indices = GradleTasksIndices.getInstance(context.project)
    return indices.getTasksCompletionVariances(context.workingDirectory)
      .asSequence().map { it.key }
  }

  private fun completeOptions(isLongOpt: Boolean): Sequence<String> {
    return GradleCommandLineOptionsProvider.getSupportedOptions().options.asSequence()
      .filterIsInstance<Option>()
      .mapNotNull { if (isLongOpt) it.longOpt else it.opt }
      .map { if (isLongOpt) "--$it" else "-$it" }
  }

  private fun completeTaskOptions(commandLine: CommandLine, context: Context): Sequence<String> {
    val task = commandLine.completedParameters.lastOrNull() ?: return emptySequence()
    return getTaskOptions(context, task).map { it.name }
  }

  private fun completeTaskClassArguments(commandLine: CommandLine, context: Context): Sequence<String> {
    if (commandLine.completedParameters.size < 2) return emptySequence()
    val task = commandLine.completedParameters[commandLine.completedParameters.size - 2]
    val optionName = commandLine.completedParameters[commandLine.completedParameters.size - 1]
    val options = getTaskOptions(context, task)
    val option = options.find { optionName == it.name } ?: return emptySequence()
    if (!option.argumentTypes.contains(TaskOption.ArgumentType.CLASS)) return emptySequence()
    val callChain = when {
      !commandLine.toComplete.contains(".") -> "*"
      else -> substringBeforeLast(commandLine.toComplete, ".") + "."
    }
    val result = ConcurrentLinkedQueue<String>()
    val model = GotoClassModel2(context.project)
    val parameters = FindSymbolParameters.simple(context.project, false)
    model.processNames({ result.add("$callChain$it") }, parameters)
    return result.toList().asSequence()
  }

  private fun getTaskOptions(context: Context, task: String): Sequence<TaskOption> {
    val indices = GradleTasksIndices.getInstance(context.project)
    val provider = GradleCommandLineTaskOptionsProvider()
    return indices.findTasks(context.workingDirectory, task)
      .flatMap { provider.getTaskOptions(it) }
      .asSequence()
  }

  private fun createContext(project: Project, context: RunAnythingContext, dataContext: DataContext): Context? {
    val workingDirectory = context.getWorkingDirectory() ?: return null
    val executor = EXECUTOR_KEY.getData(dataContext)
    return Context(context, project, workingDirectory, executor)
  }

  private fun RunAnythingContext.getWorkingDirectory(): String? {
    return when (this) {
      is ProjectContext -> getLinkedProjectPath() ?: getPath()
      is ModuleContext -> getLinkedModulePath() ?: getPath()
      else -> getPath()
    }
  }

  private fun ProjectContext.getLinkedProjectPath(): String? {
    return GradleSettings.getInstance(project)
      .linkedProjectsSettings.firstOrNull()
      ?.let { findProjectData(project, SYSTEM_ID, it.externalProjectPath) }
      ?.data?.linkedExternalProjectPath
  }

  private fun ModuleContext.getLinkedModulePath(): String? {
    return ExternalSystemApiUtil.getExternalProjectPath(module)
  }

  private fun Module.isSourceRoot(): Boolean {
    return GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(this)
  }

  data class Context(
    val context: RunAnythingContext,
    val project: Project,
    val workingDirectory: String,
    val executor: Executor?
  )

  companion object {
    const val HELP_COMMAND = "gradle"
    private val SECONDARY_HELP_COMMANDS = listOf("gradlew", "./gradlew", "gradle.bat")
  }
}
