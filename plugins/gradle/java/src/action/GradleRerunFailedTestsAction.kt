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
import com.intellij.execution.actions.JavaRerunFailedTestsAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.*
import org.jetbrains.plugins.gradle.util.containsSubSequenceInSequence
import org.jetbrains.plugins.gradle.util.containsTasksInScriptParameters
import java.util.*

/**
 * @author Vladislav.Soroka
 */
class GradleRerunFailedTestsAction(consoleView: GradleTestsExecutionConsole) : JavaRerunFailedTestsAction(consoleView.console,
                                                                                                          consoleView.properties) {
  override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile {
    val configuration = myConsoleProperties.configuration as ExternalSystemRunConfiguration
    val failedTests = getFailedTests(configuration.project)
    return object : MyRunProfile(configuration) {
      override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val runProfile = (peer as ExternalSystemRunConfiguration).clone()
        val project = runProfile.project
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val projectScope = GlobalSearchScope.projectScope(project)
        val settings = runProfile.settings.clone()
        val tests = ContainerUtil.filterIsInstance(failedTests, GradleSMTestProxy::class.java)
        val findTestSource = label@{ test: GradleSMTestProxy ->
          test.className?.let { className ->
            getSourceFile(javaPsiFacade.findClass(className, projectScope))
          }
        }
        val createFilter = { test: GradleSMTestProxy ->
          TestMethodGradleConfigurationProducer.createTestFilter(test.className, test.name)
        }
        val getTestsTaskToRun = { source: VirtualFile ->
          val foundTasksToRun = GradleTestRunConfigurationProducer.findAllTestsTaskToRun(source, project)
          val tasksToRun = ArrayList<List<String>>()
          var isSpecificTask = false
          for (tasks in foundTasksToRun) {
            val escapedTasks = ContainerUtil.map(tasks) { it.escapeIfNeeded() }
            if (containsSubSequenceInSequence(runProfile.settings.taskNames, escapedTasks) ||
                containsTasksInScriptParameters(runProfile.settings.scriptParameters, escapedTasks)) {
              ContainerUtil.addAllNotNull(tasksToRun, tasks)
              isSpecificTask = true
            }
          }
          if (!isSpecificTask && !foundTasksToRun.isEmpty()) {
            ContainerUtil.addAllNotNull(tasksToRun, foundTasksToRun.iterator().next())
          }
          tasksToRun
        }
        val projectPath = settings.externalProjectPath
        if (settings.applyTestConfiguration(projectPath, tests, findTestSource, createFilter, getTestsTaskToRun)) {
          runProfile.settings.setFrom(settings)
        }
        return runProfile.getState(executor, environment)
      }
    }
  }
}