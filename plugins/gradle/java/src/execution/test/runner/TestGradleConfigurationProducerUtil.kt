// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Experimental
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findTestsTaskToRun

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  testTasksToRun: List<Map<String, List<String>>>,
  sourceElements: Iterable<E>,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(module, testTasksToRun, sourceElements, ::getSourceFile, createFilter)
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  sourceElements: Iterable<E>,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(module, sourceElements, ::getSourceFile, createFilter)
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  testTasksToRun: List<Map<String, List<String>>>,
  vararg sourceElements: E,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(module, testTasksToRun, sourceElements.asIterable(), createFilter)
}

fun <E : PsiElement> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  vararg sourceElements: E,
  createFilter: (E) -> String
): Boolean {
  return applyTestConfiguration(module, sourceElements.asIterable(), createFilter)
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  testTasksToRun: List<Map<String, List<String>>>,
  tests: Iterable<T>,
  findTestSource: (T) -> VirtualFile?,
  createFilter: (T) -> String): Boolean {
  return applyTestConfiguration(module, tests, findTestSource, createFilter) { source ->
    testTasksToRun.mapNotNull { it[source.path] }
  }
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  tests: Iterable<T>,
  findTestSource: (T) -> VirtualFile?,
  createFilter: (T) -> String): Boolean {
  return applyTestConfiguration(module, tests, findTestSource, createFilter) { source ->
    listOf(findTestsTaskToRun(source, module.project))
  }
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  module: Module,
  tests: Iterable<T>,
  findTestSource: (T) -> VirtualFile?,
  createFilter: (T) -> String,
  getTestsTaskToRun: (VirtualFile) -> List<List<String>>
): Boolean {
  if (!GradleRunnerUtil.isGradleModule(module)) return false
  var projectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false
  CachedModuleDataFinder.getGradleModuleData(module)?.also {
    val isGradleProjectDirUsedToRunTasks = it.directoryToRunTask == it.gradleProjectDir
    if (!isGradleProjectDirUsedToRunTasks) {
      projectPath = it.directoryToRunTask
    }
  }
  return applyTestConfiguration(projectPath, tests, findTestSource, createFilter, getTestsTaskToRun)
}

fun <T> ExternalSystemTaskExecutionSettings.applyTestConfiguration(
  projectPath: String,
  tests: Iterable<T>,
  findTestSource: (T) -> VirtualFile?,
  createFilter: (T) -> String,
  getTestsTaskToRun: (VirtualFile) -> List<List<String>>
): Boolean {
  // escaped-tasks -> arguments
  val testRunConfigurations = LinkedHashMap<List<String>, MutableSet<String>>()
  for (test in tests) {
    val sourceFile = findTestSource(test) ?: return false
    for (tasks in getTestsTaskToRun(sourceFile)) {
      if (tasks.isEmpty()) continue
      val escapedTasks = tasks.map { it.escapeIfNeeded() }
      val arguments = testRunConfigurations.getOrPut(escapedTasks, ::LinkedHashSet)
      val testFilter = createFilter(test).trim()
      if (testFilter.isNotEmpty()) {
        arguments.add(testFilter)
      }
    }
  }

  externalProjectPath = projectPath
  taskNames = testRunConfigurations.entries.flatMap { it.key + it.value }
  scriptParameters = if (testRunConfigurations.size > 1) "--continue" else ""

  return true
}

fun String.escapeIfNeeded() = when {
  contains(' ') -> "'$this'"
  else -> this
}

fun getSourceFile(sourceElement: PsiElement?): VirtualFile? {
  if (sourceElement == null) return null
  if (sourceElement is PsiFileSystemItem) {
    return sourceElement.virtualFile
  }
  val containingFile = sourceElement.containingFile
  if (containingFile != null) {
    return containingFile.virtualFile
  }
  return null
}
