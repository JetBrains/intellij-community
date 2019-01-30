// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findTestsTaskToRun
import java.util.*


fun ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  testTasksToRun: List<Map<String, List<String>>>,
  vararg containingClasses: PsiClass,
  createFilter: (PsiClass) -> String
): Boolean {
  return applyTestConfiguration(project, containingClasses.toList(), { it }, { it, _ -> createFilter(it) }) { source ->
    testTasksToRun.mapNotNull { it[source.path] }
  }
}

fun ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  vararg containingClasses: PsiClass,
  createFilter: (PsiClass) -> String
): Boolean {
  return applyTestConfiguration(project, containingClasses.toList(), { it }) { it, _ ->
    createFilter(it)
  }
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  tests: Iterable<T>,
  findPsiClass: (T) -> PsiClass?,
  createFilter: (PsiClass, T) -> String): Boolean {
  return applyTestConfiguration(project, tests, findPsiClass, createFilter) { source ->
    listOf(findTestsTaskToRun(source, project))
  }
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  tests: Iterable<T>,
  findPsiClass: (T) -> PsiClass?,
  createFilter: (PsiClass, T) -> String,
  getTestsTaskToRun: (VirtualFile) -> List<List<String>>
): Boolean {
  val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)
  val testRunConfigurations = LinkedHashMap<String, Pair<VirtualFile, MutableList<String>>>()
  for (test in tests) {
    val psiClass = findPsiClass(test) ?: return false
    val psiFile = psiClass.containingFile ?: return false
    val virtualFile = psiFile.virtualFile
    val module = projectFileIndex.getModuleForFile(virtualFile) ?: return false
    externalProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
    if (!GradleRunnerUtil.isGradleModule(module)) return false
    val (_, arguments) = testRunConfigurations.getOrPut(module.name) { Pair(virtualFile, ArrayList()) }
    arguments.add(createFilter(psiClass, test))
  }
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

