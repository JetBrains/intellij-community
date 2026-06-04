// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.JavaRunConfigurationExtensionManager
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.TestTasksChooser.Companion.contextWithLocationName
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine.Companion.parse
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTasks
import org.jetbrains.plugins.gradle.util.createTestWildcardFilter
import java.util.StringJoiner
import java.util.function.Consumer

private const val DEFAULT_TEST_TASK_NAME = "test"

abstract class AbstractGradleTestRunConfigurationProducer<E : PsiElement, Ex : PsiElement> : GradleTestRunConfigurationProducer() {

  protected abstract fun getElement(context: ConfigurationContext): E?

  protected abstract fun getLocationName(context: ConfigurationContext, element: E): String

  protected abstract fun suggestConfigurationName(context: ConfigurationContext, element: E, chosenElements: List<Ex>): String

  protected abstract fun chooseSourceElements(context: ConfigurationContext, element: E, onElementsChosen: Consumer<List<Ex>>)

  private fun chooseSourceElements(context: ConfigurationContext, element: E, onElementsChosen: (List<Ex>) -> Unit) {
    chooseSourceElements(context, element, Consumer { onElementsChosen(it) })
  }

  protected abstract fun getAllTestsTaskToRun(context: ConfigurationContext, element: E, chosenElements: List<Ex>): List<TestTasksToRun>

  override fun findOrCreateConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContext? {
    val configurationFromContext = super.findOrCreateConfigurationFromContext(context) ?: return null
    val element = getElement(context) ?: return configurationFromContext
    if (allTestsTaskToRun(context, element).map { it.tasksToRun.testName }.toSet().size > 1) {
      (configurationFromContext.configuration as GradleRunConfiguration).name =
        suggestConfigurationName(context, element, emptyList())
    }
    return configurationFromContext
  }

  private fun getAllTasksAndArguments(context: ConfigurationContext, element: E, chosenElements: List<Ex>): List<GradleCommandLineTasks> {
    return getAllTestsTaskToRun(context, element, chosenElements)
      .map { it.toTasksAndArguments() }
  }

  private fun allTestsTaskToRun(context: ConfigurationContext, element: E): List<TestTasksToRun> {
    return getAllTestsTaskToRun(context, element, emptyList())
  }

  override fun doSetupConfigurationFromContext(
    configuration: GradleRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>
  ): Boolean {
    val project = context.project ?: return false
    val module = context.module ?: return false
    val externalProjectPath = resolveProjectPath(module) ?: return false
    val location = context.location ?: return false
    val element = getElement(context) ?: return false
    val allTasksAndArguments = getAllTasksAndArguments(context, element, emptyList())
    val tasksAndArguments = allTasksAndArguments.firstOrNull() ?: return false

    sourceElement.set(element)
    configuration.name = suggestConfigurationName(context, element, emptyList())
    setUniqueNameIfNeeded(project, configuration)
    configuration.settings.externalProjectPath = externalProjectPath
    configuration.settings.taskNames = tasksAndArguments.tokens
    configuration.settings.scriptParameters = ""

    JavaRunConfigurationExtensionManager.instance.extendCreatedConfiguration(configuration, location)
    return true
  }

  override fun doIsConfigurationFromContext(
    configuration: GradleRunConfiguration,
    context: ConfigurationContext
  ): Boolean {
    val module = context.module ?: return false
    val externalProjectPath = resolveProjectPath(module) ?: return false
    val element = getElement(context) ?: return false
    val allTestsTaskToRun = allTestsTaskToRun(context, element)
    if (allTestsTaskToRun.map { it.tasksToRun.testName }.toSet().size > 1) {
      return false
    }
    val allTasksAndArguments = allTestsTaskToRun.map { it.toTasksAndArguments() }
    val tasksAndArguments = configuration.commandLine.tasks.tokens
    return externalProjectPath == configuration.settings.externalProjectPath &&
           tasksAndArguments.isNotEmpty() && allTasksAndArguments.isNotEmpty() &&
           isConsistedFrom(tasksAndArguments, allTasksAndArguments.map { it.tokens })
  }

  override fun onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
    val project = context.project
    val element = getElement(context)
    if (project == null || element == null) {
      LOG.warn("Cannot extract configuration data from context, uses raw run configuration")
      super.onFirstRun(configuration, context, startRunnable)
      return
    }
    val runConfiguration = configuration.configuration as GradleRunConfiguration
    val dataContext = contextWithLocationName(context.dataContext, getLocationName(context, element))
    chooseSourceElements(context, element) { elements ->
      val allTestsToRun = getAllTestsTaskToRun(context, element, elements)
        .groupBy { it.tasksToRun.testName }
        .mapValues { it.value }
      testTasksChooser.chooseTestTasks(project, dataContext, allTestsToRun) { chosenTestsToRun ->
        val chosenTasksAndArguments = chosenTestsToRun.flatten()
          .groupBy { it.tasksToRun }
          .mapValues { it.value.map(TestTasksToRun::testFilter).toSet() }
          .map { createTasksAndArguments(it.key, it.value) }

        runConfiguration.settings.taskNames = chosenTasksAndArguments.flatMap { it.tokens }
        runConfiguration.settings.scriptParameters = if (chosenTasksAndArguments.size > 1) "--continue" else ""
        runConfiguration.name = suggestConfigurationName(context, element, elements)
          .withGradleTestTaskName(chosenTestsToRun)
        if (!hasSameExistingConfiguration(runConfiguration)) {
          setUniqueNameIfNeeded(project, runConfiguration)
        }

        super.onFirstRun(configuration, context, startRunnable)
      }
    }
  }

  private fun String.withGradleTestTaskName(chosenTestsToRun: List<List<TestTasksToRun>>): String {
    val testTaskName = chosenTestsToRun.flatten().map { it.tasksToRun.testName }.distinct().singleOrNull()
    if (testTaskName == null || testTaskName == DEFAULT_TEST_TASK_NAME) return this
    if (endsWith(".$DEFAULT_TEST_TASK_NAME'")) {
      return removeSuffix(".$DEFAULT_TEST_TASK_NAME'") + ".$testTaskName'"
    }
    if (endsWith("'")) {
      return dropLast(1) + ".$testTaskName'"
    }
    return "$this.$testTaskName"
  }

  private fun hasSameExistingConfiguration(configuration: GradleRunConfiguration): Boolean {
    val taskNames = configuration.commandLine.tasks.tokens
    val scriptParameters = configuration.settings.scriptParameters
    return RunManager.getInstance(configuration.project)
      .getConfigurationSettingsList(configurationFactory.type)
      .any { settings ->
        val existingConfiguration = settings.configuration as? GradleRunConfiguration ?: return@any false
        existingConfiguration !== configuration &&
        existingConfiguration.name == configuration.name &&
        existingConfiguration.settings.externalProjectPath == configuration.settings.externalProjectPath &&
        existingConfiguration.commandLine.tasks.tokens == taskNames &&
        existingConfiguration.settings.scriptParameters == scriptParameters
      }
  }

  /**
   * Checks that [list] can be represented by sequence from all or part of [subLists].
   *
   * For example:
   *
   * `[1, 2, 3, 4] is not consisted from [1, 2]`
   *
   * `[1, 2, 3, 4] is consisted from [1, 2] and [3, 4]`
   *
   * `[1, 2, 3, 4] is consisted from [1, 2], [3, 4] and [1, 2, 3]`
   *
   * `[1, 2, 3, 4] is not consisted from [1, 2, 3] and [3, 4]`
   */
  private fun isConsistedFrom(list: List<String>, subLists: List<List<String>>): Boolean {
    val reducer = ArrayList(list)
    val sortedTiles = subLists.sortedByDescending { it.size }
    for (tile in sortedTiles) {
      val size = tile.size
      val index = indexOfSubList(reducer, tile)
      if (index >= 0) {
        val subReducer = reducer.subList(index, index + size)
        subReducer.clear()
        subReducer.add(null)
      }
    }
    return ContainerUtil.and(reducer) { it == null }
  }

  private fun indexOfSubList(list: List<String>, subList: List<String>): Int {
    for (i in list.indices) {
      if (i + subList.size <= list.size) {
        var hasSubList = true
        for (j in subList.indices) {
          if (list[i + j] != subList[j]) {
            hasSubList = false
            break
          }
        }
        if (hasSubList) {
          return i
        }
      }
    }
    return -1
  }

  private fun createTasksAndArguments(tasksToRun: TasksToRun, testFilters: Collection<String>): GradleCommandLineTasks {
    val commandLineBuilder = StringJoiner(" ")
    for (task in tasksToRun) {
      commandLineBuilder.add(task.escapeIfNeeded())
    }
    if (createTestWildcardFilter() !in testFilters) {
      for (testFilter in testFilters) {
        if (StringUtil.isNotEmpty(testFilter)) {
          commandLineBuilder.add(testFilter)
        }
      }
    }
    val commandLine = commandLineBuilder.toString()
    return parse(commandLine).tasks
  }

  private fun TestTasksToRun.toTasksAndArguments() = createTasksAndArguments(tasksToRun, listOf(testFilter))

  class TestTasksToRun(val tasksToRun: TasksToRun, val testFilter: String)
}