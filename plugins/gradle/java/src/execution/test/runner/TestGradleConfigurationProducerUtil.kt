// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import java.util.*

private val LOG = Logger.getInstance("org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducerUtil")

fun <T> applyTestConfiguration(
  project: Project,
  settings: ExternalSystemTaskExecutionSettings,
  tests: Iterable<T>,
  findPsiClass: (T) -> PsiClass?,
  createFilter: (PsiClass, T) -> String
): Boolean {
  val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)

  var module: Module? = null
  val scriptParameters = StringJoiner(" ")
  for (test in tests) {
    val psiClass = findPsiClass(test) ?: continue
    val psiFile = psiClass.containingFile ?: continue
    val moduleForFile = projectFileIndex.getModuleForFile(psiFile.virtualFile)

    // Todo: fix it sometime later.
    if (module != null && moduleForFile != null && module.name != moduleForFile.name) {
      LOG.warn("BUG -- create run configuration for classes in different modules: $module and $moduleForFile")
      LOG.warn("Now it is impossible to implements by the `ExternalSystemTaskExecutionSettings` model.")
      continue
    }
    // Needed for detection wrong state of test run configurations
    module = moduleForFile

    scriptParameters.add(createFilter(psiClass, test))
  }

  if (module == null) return false
  if (!GradleRunnerUtil.isGradleModule(module)) return false
  val projectPath = GradleRunnerUtil.resolveProjectPath(module)
  if (projectPath == null) return false
  val tasksToRun = GradleTestRunConfigurationProducer.getTasksToRun(module)
  if (tasksToRun.isEmpty()) return false

  settings.externalProjectPath = projectPath
  settings.taskNames = ContainerUtil.newArrayList(tasksToRun)
  settings.scriptParameters = scriptParameters.toString().trim()

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
