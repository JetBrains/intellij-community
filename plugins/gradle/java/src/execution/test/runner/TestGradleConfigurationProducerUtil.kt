// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findTestsTaskToRun
import java.util.*

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  testTasksToRun: List<Map<String, List<String>>>,
  sourceElements: Iterable<E>,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(project, sourceElements, { it }, { it, _ -> createFilter(it) }) { source ->
    testTasksToRun.mapNotNull { it[source.path] }
  }
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  sourceElements: Iterable<E>,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(project, sourceElements, { it }, { it, _ -> createFilter(it) })
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  testTasksToRun: List<Map<String, List<String>>>,
  vararg sourceElements: E,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(project, sourceElements.toList(), { it }, { it, _ -> createFilter(it) }) { source ->
    testTasksToRun.mapNotNull { it[source.path] }
  }
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  vararg sourceElements: E,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(project, sourceElements.toList(), { it }) { it, _ ->
    createFilter(it)
  }
}

fun <E : PsiElement, T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  testTasksToRun: List<Map<String, List<String>>>,
  tests: Iterable<T>,
  findSourceElement: (T) -> E?,
  createFilter: (E, T) -> String): Boolean {
  return applyTestConfiguration(project, tests, findSourceElement, createFilter) { source ->
    testTasksToRun.mapNotNull { it[source.path] }
  }
}

fun <E : PsiElement, T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  tests: Iterable<T>,
  findSourceElement: (T) -> E?,
  createFilter: (E, T) -> String): Boolean {
  return applyTestConfiguration(project, tests, findSourceElement, createFilter) { source ->
    listOf(findTestsTaskToRun(source, project))
  }
}

fun <E : PsiElement, T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  tests: Iterable<T>,
  findSourceElement: (T) -> E?,
  createFilter: (E, T) -> String,
  getTestsTaskToRun: (VirtualFile) -> List<List<String>>
): Boolean {
  val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)
  val testRunConfigurations = LinkedHashMap<String, Pair<VirtualFile, MutableList<String>>>()
  var module: Module? = null
  for (test in tests) {
    val sourceElement = findSourceElement(test) ?: return false
    val sourceFile = getSourceFile(sourceElement) ?: return false
    module = projectFileIndex.getModuleForFile(sourceFile) ?: return false
    if (!GradleRunnerUtil.isGradleModule(module)) return false
    val (_, arguments) = testRunConfigurations.getOrPut(module.name) { Pair(sourceFile, ArrayList()) }
    arguments.add(createFilter(sourceElement, test))
  }
  if (module == null) return false
  externalProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  val taskSettings = ArrayList<Pair<String, List<String>>>()
  val unorderedParameters = ArrayList<String>()
  for ((source, arguments) in testRunConfigurations.values) {
    for (tasks in getTestsTaskToRun(source)) {
      if (tasks.isEmpty()) continue
      for (task in tasks.dropLast(1)) {
        taskSettings.add(task to emptyList())
      }
      val last = tasks.last()
      taskSettings.add(last to arguments)
    }
  }
  if (testRunConfigurations.size > 1) {
    unorderedParameters.add("--continue")
  }
  setFrom(taskSettings, unorderedParameters)
  return true
}

private fun ExternalSystemTaskExecutionSettings.setFrom(taskSettings: List<Pair<String, List<String>>>, unorderedParameters: List<String>) {
  val hasTasksAfterTaskWithArguments = taskSettings.dropWhile { it.second.isEmpty() }.size > 1
  if (hasTasksAfterTaskWithArguments) {
    val joiner = StringJoiner(" ")
    for ((task, arguments) in taskSettings) {
      when {
        task.contains(' ') -> joiner.add("'$task'")
        else -> joiner.add(task)
      }
      joiner.addAll(arguments)
    }
    joiner.addAll(unorderedParameters)
    taskNames = emptyList()
    scriptParameters = joiner.toString()
  }
  else {
    val joiner = StringJoiner(" ")
    joiner.addAll(taskSettings.lastOrNull()?.second ?: emptyList())
    joiner.addAll(unorderedParameters)
    taskNames = taskSettings.map { it.first }
    scriptParameters = joiner.toString()
  }
}

private fun StringJoiner.addAll(elements: Iterable<String>) = apply {
  for (element in elements) {
    add(element)
  }
}

fun getSourceFile(sourceElement: PsiElement): VirtualFile? {
  if (sourceElement is PsiFileSystemItem) {
    return sourceElement.virtualFile
  }
  val containingFile = sourceElement.containingFile
  if (containingFile != null) {
    return containingFile.virtualFile
  }
  return null
}
