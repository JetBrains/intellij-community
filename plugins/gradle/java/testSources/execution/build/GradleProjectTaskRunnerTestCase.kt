// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.impl.EmptyCompileScopeBuildTaskImpl
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import com.intellij.task.impl.ModuleBuildTaskImpl
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions

@GradleProjectTestApplication
abstract class GradleProjectTaskRunnerTestCase : GradleProjectTestCase() {

  @Suppress("SameParameterValue")
  fun `test GradleProjectTaskRunner#canRun`(
    configurationType: ConfigurationType,
    shouldRunWithModule: Boolean,
    shouldRunWithoutModule: Boolean,
    shouldBuildWithModule: Boolean,
    shouldBuildWithoutModule: Boolean,
  ) {
    Assertions.assertTrue(GradleProjectSettings.isDelegatedBuildEnabled(module)) {
      "Build and run actions should be delegated to Gradle"
    }

    run {
      val configuration = createTestConfiguration(configurationType, module)
      val executeTask = ExecuteRunConfigurationTaskImpl(configuration)
      `assert GradleProjectTaskRunner#canRun`(executeTask, null, shouldRunWithModule) {
        configurationType.displayName + " configuration"
      }
    }

    run {
      val configuration = createTestConfiguration(configurationType)
      val executeTask = ExecuteRunConfigurationTaskImpl(configuration)
      `assert GradleProjectTaskRunner#canRun`(executeTask, null, shouldRunWithoutModule) {
        configurationType.displayName + " configuration without module"
      }
    }

    run {
      val configuration = createTestConfiguration(configurationType, module)
      val buildTask = ModuleBuildTaskImpl(module)
      val buildTaskContext = ProjectTaskContext(Any(), configuration)
      `assert GradleProjectTaskRunner#canRun`(buildTask, buildTaskContext, shouldBuildWithModule) {
        configurationType.displayName + " module build task"
      }
    }

    run {
      val configuration = createTestConfiguration(configurationType)
      val buildTask = EmptyCompileScopeBuildTaskImpl(true)
      val buildTaskContext = ProjectTaskContext(Any(), configuration)
      `assert GradleProjectTaskRunner#canRun`(buildTask, buildTaskContext, shouldBuildWithoutModule) {
        configurationType.displayName + " empty scope build task"
      }
    }
  }

  private fun `assert GradleProjectTaskRunner#canRun`(
    projectTask: ProjectTask,
    context: ProjectTaskContext?,
    expected: Boolean,
    nameSupplier: () -> String,
  ) {
    val taskRunner = GradleProjectTaskRunner()
    val actual = taskRunner.canRun(project, projectTask, context)
    Assertions.assertEquals(expected, actual) {
      val name = nameSupplier()
      when (expected) {
        true -> "$name should run by Gradle."
        else -> "$name shouldn't run by Gradle."
      }
    }
  }

  fun createTestConfiguration(configurationType: ConfigurationType): RunConfiguration {
    val configurationFactory = configurationType.configurationFactories.single()
    val templateConfiguration = configurationFactory.createTemplateConfiguration(project)
    val configuration = configurationFactory.createConfiguration("Test configuration", templateConfiguration)
    return configuration
  }

  fun createTestConfiguration(configurationType: ConfigurationType, module: Module): RunConfiguration {
    val configuration = createTestConfiguration(configurationType)
    configuration.module = module
    return configuration
  }

  private var RunConfiguration.module: Module?
    get() {
      this as ModuleBasedConfiguration<*, *>
      return configurationModule.module
    }
    set(value) {
      this as ModuleBasedConfiguration<*, *>
      configurationModule.module = value
    }
}