// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.Executor
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.annotations.ApiStatus.Experimental


/**
 * If you want to use your own [ExecutionEnvironment] from [ApplicationConfiguration] instead of [GradleBaseApplicationEnvironmentProvider],
 * extend [CustomGradleApplicationEnvironmentProvider] or [GradleApplicationEnvironmentProvider]
 */
@Experimental
abstract class CustomGradleApplicationEnvironmentProvider<T : ApplicationConfiguration> : GradleApplicationEnvironmentProvider() {
  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean = getRunConfiguration(task) != null

  override fun createExecutionEnvironment(project: Project,
                                          executeRunConfigurationTask: ExecuteRunConfigurationTask,
                                          executor: Executor?): ExecutionEnvironment? {
    val applicationConfiguration = getRunConfiguration(executeRunConfigurationTask) ?: return null
    val module = applicationConfiguration.configurationModule.module ?: return null

    if (!shouldRunUsingGradle(applicationConfiguration) && findGenerator(module) == null) return null

    val environment =
      ReadAction.compute<ExecutionEnvironment?, RuntimeException> {
        super.createExecutionEnvironment(project, executeRunConfigurationTask, executor)
      } ?: return null

    return customiseExecutionEnvironment(project, applicationConfiguration, environment)
  }

  abstract fun shouldRunUsingGradle(runConfiguration: T): Boolean

  abstract fun customiseExecutionEnvironment(project: Project,
                                             runConfiguration: T,
                                             environment: ExecutionEnvironment): ExecutionEnvironment

  abstract fun getRunConfiguration(task: ExecuteRunConfigurationTask): T?

  override fun generateInitScript(params: GradleInitScriptParameters): String? {
    val generator = findGenerator(params.module) ?: return super.generateInitScript(params)
    return generator.generateInitScript(params)
  }
}

/**
 * Tries to find complex [GradleInitScriptGenerator] e.g. for KotlinMultiplatform
 */
private fun findGenerator(module: Module): GradleInitScriptGenerator? {
  return ReadAction.compute(
    ThrowableComputable<GradleInitScriptGenerator?, RuntimeException> {
      GradleInitScriptGenerator.findGenerator(module)
    })
}
