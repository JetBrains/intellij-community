// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.Location
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.execution.TaskSettingsImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.getTasksToRun
import org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom
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
  resetTaskSettings()
  resetUnorderedParameters()
  externalProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  for ((testModule, arguments) in testRunConfigurations.values) {
    val tasks = getTasksToRun(testModule)
    if (tasks.isEmpty()) continue
    for (task in tasks.dropLast(1)) {
      val taskSettings = TaskSettingsImpl(task)
      addTaskSettings(taskSettings)
    }
    val last = tasks.last()
    val taskSettings = TaskSettingsImpl(last, arguments)
    addTaskSettings(taskSettings)
  }
  if (testRunConfigurations.size > 1) {
    addUnorderedParameter("--continue")
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

fun ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  location: Location<*>?,
  psiMethod: PsiMethod,
  vararg containingClasses: PsiClass
): Boolean {
  return applyTestConfiguration(project, *containingClasses) { psiClass ->
    createTestFilterFrom(location, psiClass, psiMethod)
  }
}

fun ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  project: Project,
  vararg containingClasses: PsiClass
): Boolean {
  return applyTestConfiguration(project, *containingClasses) { psiClass ->
    createTestFilterFrom(psiClass)
  }
}
