// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.runWriteActionAndWait
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.gradle.importing.GradleSettingsImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.util.*
import kotlin.time.Duration.Companion.minutes

/**
 * @author Vladislav.Soroka
 */
abstract class GradleApplicationEnvironmentProviderTestCase : GradleSettingsImportingTestCase() {

  override fun setUp() {
    super.setUp()
    currentExternalProjectSettings.delegatedBuild = true
  }

  fun assertAppRunOutput(configurationSettings: RunnerAndConfigurationSettings, vararg checks: String) {
    val tracer = ExternalSystemExecutionTracer()
    tracer.traceExecution {
      runAppAndWait(configurationSettings)
    }
    val output = tracer.output.joinToString("")
    for (check in checks) {
      Assertions.assertThat(output)
        .contains(check)
    }
  }

  private fun runAppAndWait(configurationSettings: RunnerAndConfigurationSettings) {
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val environment = runWriteActionAndWait {
      ExecutionEnvironmentBuilder.create(executor, configurationSettings)
        .contentToReuse(null)
        .dataContext(null)
        .activeTarget()
        .build()
    }

    withThreadDumpEvery(1.minutes) {
      waitForGradleEventDispatcherClosing {
        waitForAnyExecution(project) {
          waitForAnyGradleTaskExecution {
            ProgramRunnerUtil.executeConfiguration(environment, false, true)
          }
        }
      }
    }
  }
}