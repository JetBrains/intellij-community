// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.getTasksToRun
import java.util.*

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  tests: Iterable<T>,
  findPsiClass: (T) -> PsiClass?,
  createFilter: (PsiClass, T) -> String
): Boolean {
  val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)
  val testRunConfigurations = LinkedHashMap<String, Pair<Module, MutableList<String>>>()
  for (test in tests) {
    val psiClass = findPsiClass(test) ?: continue
    val psiFile = psiClass.containingFile ?: continue
    val module = projectFileIndex.getModuleForFile(psiFile.virtualFile) ?: return false
    if (!GradleRunnerUtil.isGradleModule(module)) return false
    val (_, arguments) = testRunConfigurations.getOrPut(module.name) { Pair(module, ArrayList()) }
    arguments.add(createFilter(psiClass, test))
  }
  val (module, _) = testRunConfigurations.values.firstOrNull() ?: return false
  val taskSettings = ArrayList<Pair<String, List<String>>>()
  val unorderedParameters = ArrayList<String>()
  externalProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  for ((testModule, arguments) in testRunConfigurations.values) {
    val tasks = getTasksToRun(testModule)
    if (tasks.isEmpty()) continue
    for (task in tasks.dropLast(1)) {
      taskSettings.add(task to emptyList())
    }
    val last = tasks.last()
    taskSettings.add(last to arguments)
  }
  if (testRunConfigurations.size > 1) {
    unorderedParameters.add("--continue")
  }

  val hasTasksAfterTaskWithArguments = taskSettings.dropWhile { it.second.isEmpty() }.size > 1
  if (hasTasksAfterTaskWithArguments) {
    val joiner = StringJoiner(" ")
    for ((task, arguments) in taskSettings) {
      joiner.add(task)
      for (argument in arguments) {
        joiner.add(argument)
      }
    }
    for (argument in unorderedParameters) {
      joiner.add(argument)
    }
    taskNames = emptyList()
    scriptParameters = joiner.toString()
  }
  else {
    val arguments = taskSettings.lastOrNull()?.second ?: emptyList()
    val joiner = StringJoiner(" ")
    for (argument in arguments) {
      joiner.add(argument)
    }
    for (argument in unorderedParameters) {
      joiner.add(argument)
    }
    taskNames = taskSettings.map { it.first }
    scriptParameters = joiner.toString()
  }
  return true
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
