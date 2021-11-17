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
import org.jetbrains.plugins.gradle.execution.test.runner.GradleSMTestProxy
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.execution.test.runner.getSourceFile
import org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom

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
    val failedTests = getFailedTests(project)
      .filterIsInstance<GradleSMTestProxy>()
      .map { getTestLocationInfo(project, it) }
    val findTestSource = { it: TestLocationInfo -> getSourceFile(it.element) }
    val createFiler = { it: TestLocationInfo -> createTestFilterFrom(it.location, it.psiClass, it.psiMethod, true) }

    val tasksToRun = taskNames
      .takeWhile { !it.startsWith("--") } // gradle filter is out of interest, created from scratch
      .flatMap { it.split(" ") }
    val formattedTasksToRun = listOf(tasksToRun)
    val getTestsTaskToRun = { _: VirtualFile -> formattedTasksToRun }
    if (!applyTestConfiguration(externalProjectPath, failedTests, findTestSource, createFiler, getTestsTaskToRun)) {
      LOG.warn("Cannot apply test configuration, uses previous run configuration")
    }
  }

  private fun getTestLocationInfo(project: Project, testProxy: GradleSMTestProxy): TestLocationInfo {
    val projectScope = GlobalSearchScope.projectScope(project)
    val location = testProxy.getLocation(project, projectScope)
    return when (val element = location?.psiElement) {
      is PsiClass -> TestLocationInfo(location, element, element)
      is PsiMethod -> TestLocationInfo(location, element, element.containingClass, element)
      else -> TestLocationInfo(location)
    }
  }

  private data class TestLocationInfo(
    val location: Location<*>?,
    val element: PsiElement? = null,
    val psiClass: PsiClass? = null,
    val psiMethod: PsiMethod? = null
  )

  companion object {
    private val LOG = Logger.getInstance(GradleRerunFailedTestsAction::class.java)
  }
}