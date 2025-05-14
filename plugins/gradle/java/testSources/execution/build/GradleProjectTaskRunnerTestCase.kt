// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.util.setKotlinProperty
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import com.intellij.task.impl.ModuleBuildTaskImpl
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions

@GradleProjectTestApplication
abstract class GradleProjectTaskRunnerTestCase : GradleProjectTestCase() {

  private val projectSettings: GradleProjectSettings
    get() = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath)!!

  private var delegatedBuild: Boolean
    get() = projectSettings.delegatedBuild
    set(value) = projectSettings.setDelegatedBuild(value)

  private var delegatedRun: Boolean
    get() = AdvancedSettings.getBoolean("gradle.run.using.gradle")
    set(value) = AdvancedSettings.setBoolean("gradle.run.using.gradle", value)

  fun setupGradleDelegationMode(delegatedBuild: Boolean, delegatedRun: Boolean, disposable: Disposable) {
    setKotlinProperty(::delegatedBuild, delegatedBuild, disposable)
    setKotlinProperty(::delegatedRun, delegatedRun, disposable)
  }

  fun assertGradleProjectTaskRunnerCanRun(
    configurationType: ConfigurationType,
    shouldBuild: Boolean,
    shouldRun: Boolean,
  ) {
    val projectTaskRunner = ProjectTaskRunner.EP_NAME.findExtensionOrFail(GradleProjectTaskRunner::class.java)

    val configuration = createTestConfiguration(configurationType, module)

    val buildTask = ModuleBuildTaskImpl(module)
    val buildTaskContext = ProjectTaskContext(Any(), configuration)
    Assertions.assertEquals(shouldBuild, projectTaskRunner.canRun(project, buildTask, buildTaskContext)) {
      when (shouldBuild) {
        true -> configurationType.displayName + " run configuration's module build task should run by Gradle."
        else -> configurationType.displayName + " run configuration's module build task shouldn't run by Gradle."
      }
    }

    val executeTask = ExecuteRunConfigurationTaskImpl(configuration)
    Assertions.assertEquals(shouldRun, projectTaskRunner.canRun(project, executeTask, null)) {
      when (shouldRun) {
        true -> configurationType.displayName + " run configuration task should run by Gradle."
        else -> configurationType.displayName + " run configuration task shouldn't run by Gradle."
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