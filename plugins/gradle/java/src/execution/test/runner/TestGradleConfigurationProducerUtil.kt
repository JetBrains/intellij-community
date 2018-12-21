// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.Location
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.execution.TaskSettings
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

fun <T> applyTestConfiguration(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
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
  settings.resetTaskSettings()
  settings.resetUnorderedArguments()
  settings.externalProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  for ((testModule, arguments) in testRunConfigurations.values) {
    val tasks = getTasksToRun(testModule)
    if (tasks.isEmpty()) continue
    for (task in tasks.dropLast(1)) {
      val taskSettings = TaskSettingsImpl(task)
      settings.addTaskSettings(taskSettings)
    }
    val last = tasks.last()
    val taskSettings = TaskSettingsImpl(last, arguments)
    settings.addTaskSettings(taskSettings)
  }
  if (testRunConfigurations.size > 1) {
    settings.addUnorderedArgument("--continue")
  }
  return true
}

fun applyTestConfiguration(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
  vararg containingClasses: PsiClass,
  createFilter: (PsiClass) -> String
): Boolean {
  return applyTestConfiguration(project, settings, containingClasses.toList(), { it }) { it, _ ->
    createFilter(it)
  }
}

fun applyTestConfigurationFor(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
  location: Location<*>?,
  psiMethod: PsiMethod,
  vararg containingClasses: PsiClass
): Boolean {
  return applyTestConfiguration(project, settings, *containingClasses) { psiClass ->
    createTestFilterFrom(location, psiClass, psiMethod)
  }
}

fun applyTestConfigurationFor(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
  vararg containingClasses: PsiClass
): Boolean {
  return applyTestConfiguration(project, settings, *containingClasses) { psiClass ->
    createTestFilterFrom(psiClass)
  }
}
