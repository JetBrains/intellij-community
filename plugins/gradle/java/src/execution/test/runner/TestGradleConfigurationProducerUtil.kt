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
    val (_, scriptParameters) = testRunConfigurations.getOrPut(module.name) { Pair(module, ArrayList()) }
    scriptParameters.add(createFilter(psiClass, test))
  }
  val (module, _) = testRunConfigurations.values.firstOrNull() ?: return false
  val projectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  val scriptParameters = testRunConfigurations.values
    .map { (module, parameters) -> getTasksToRun(module) + parameters }
    .flatten()
    .fold(StringJoiner(" "), StringJoiner::add)
  if (testRunConfigurations.size > 1) {
    scriptParameters.add("--continue")
  }
  settings.externalProjectPath = projectPath
  settings.taskNames = listOf()
  settings.scriptParameters = scriptParameters.toString()
  return true
}

fun applyTestConfiguration(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
  containingClasses: Array<PsiClass>,
  createFilter: (PsiClass) -> String
): Boolean {
  return applyTestConfiguration(project, settings, containingClasses.toList(), { it }) { it, _ ->
    createFilter(it)
  }
}
