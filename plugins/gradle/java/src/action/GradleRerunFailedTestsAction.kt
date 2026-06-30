/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.action

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.actions.JavaRerunFailedTestsAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.execution.junit2.info.MethodLocation
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationInfo
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.execution.test.runner.getSourceFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine.Companion.parse
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

class GradleRerunFailedTestsAction(
  consoleView: GradleTestsExecutionConsole
) : JavaRerunFailedTestsAction(
  consoleView.console,
  consoleView.properties
) {

  private val configuration: ExternalSystemRunConfiguration
    get() = myConsoleProperties.configuration as ExternalSystemRunConfiguration

  override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile {
    val configuration = configuration.clone()
    configuration.settings.setupRerunTestConfiguration(configuration.project)
    return object : MyRunProfile(configuration) {
      override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return configuration.getState(executor, environment)
      }
    }
  }

  private fun ExternalSystemTaskExecutionSettings.setupRerunTestConfiguration(project: Project) {
    val failedTests = getFailedTests(project).filterIsInstance<GradleSMTestProxy>().mapNotNull { getTestLocationInfo(project, it) }
    if (failedTests.isEmpty()) {
      LOG.warn("Cannot resolve failed tests to rerun, using previous run configuration")
      return
    }

    val findTestSource = { it: RerunTestLocationInfo -> getSourceFile(it.element) }
    val createFilter = { it: RerunTestLocationInfo -> createRerunTestFilter(it) }

    val tasksToRun = removeTestFilters(taskNames)
    val formattedTasksToRun = listOf(tasksToRun)
    val getTestsTaskToRun = { _: VirtualFile -> formattedTasksToRun }

    if (!applyTestConfiguration(externalProjectPath, failedTests, findTestSource, createFilter, getTestsTaskToRun)) {
      LOG.warn("Cannot apply test configuration, using previous run configuration")
    }
  }

  private fun getTestLocationInfo(project: Project, testProxy: GradleSMTestProxy): RerunTestLocationInfo? {
    val projectScope = GlobalSearchScope.projectScope(project)
    val location = testProxy.getLocation(project, projectScope)
    val locationInfo = location?.let(::getRerunTestLocationInfo)
    if (locationInfo == null) {
      LOG.warn("Undefined test to rerun: ${testProxy.locationUrl}")
    }
    return locationInfo
  }

  companion object {
    private val LOG = Logger.getInstance(GradleRerunFailedTestsAction::class.java)
  }
}

@VisibleForTesting
fun getRerunTestLocationInfo(location: Location<*>): RerunTestLocationInfo? {
  if (location is GradleTestLocationInfo) {
    val testFilter = location.testFilter?.trim()?.takeIf { it.isNotEmpty() }
    if (location.testClass != null || testFilter != null) {
      return RerunTestLocationInfo(location, location.sourceElement, location.testClass, location.testMethod, testFilter)
    }
  }

  val methodLocation = GradleRunnerUtil.getMethodLocation(location)
  if (methodLocation != null) {
    val psiClass = getContainingClass(location, methodLocation)
    if (psiClass != null) {
      return RerunTestLocationInfo(location, location.psiElement, psiClass, methodLocation.psiElement)
    }
  }

  val psiClass = getTestClass(location)
  if (psiClass != null) {
    return RerunTestLocationInfo(location, location.psiElement, psiClass)
  }
  return null
}

private fun getContainingClass(location: Location<*>, methodLocation: Location<PsiMethod>): PsiClass? {
  if (location is PsiMemberParameterizedLocation) {
    return location.containingClass
  }
  if (methodLocation is MethodLocation) {
    return methodLocation.containingClass
  }
  return methodLocation.psiElement.containingClass ?: getTestClass(location)
}

private fun getTestClass(location: Location<*>): PsiClass? {
  (location.psiElement as? PsiClass)?.let {
    return it
  }
  val iterator = location.getAncestors(PsiClass::class.java, false)
  return if (iterator.hasNext()) iterator.next().psiElement else null
}

@VisibleForTesting
data class RerunTestLocationInfo(
  val location: Location<*>?,
  val element: PsiElement? = null,
  val psiClass: PsiClass? = null,
  val psiMethod: PsiMethod? = null,
  val testFilter: String? = null
)

@VisibleForTesting
fun createRerunTestFilter(locationInfo: RerunTestLocationInfo): String {
  return locationInfo.testFilter ?: createTestFilterFrom(locationInfo.location, locationInfo.psiClass, locationInfo.psiMethod)
}

@VisibleForTesting
fun removeTestFilters(taskNames: List<String>): List<String> {
  return parse(taskNames.joinToString(" ")).tasks.flatMap { task ->
    listOf(task.name) + task.options
      .filter { it.name != GradleConstants.TESTS_ARG_NAME }
      .flatMap { it.tokens }
  }
}
