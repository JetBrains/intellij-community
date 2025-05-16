// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.util.setKotlinProperty
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import com.intellij.task.impl.ModuleBuildTaskImpl
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.replaceService
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.gradle.tooling.model.build.JavaEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.connection.GradleConnectorService
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.util.function.Function

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

  fun mockGradleConnectionService(disposable: Disposable, execute: () -> Unit) {
    val connection = mockProjectConnection(execute)
    val connectorService = object : GradleConnectorService by notImplemented() {
      override fun <R> withGradleConnection(
        projectPath: String,
        taskId: ExternalSystemTaskId?,
        executionSettings: GradleExecutionSettings?,
        listener: ExternalSystemTaskNotificationListener?,
        cancellationToken: CancellationToken?,
        function: Function<ProjectConnection, R>,
      ): R = function.apply(connection)
    }
    project.replaceService(GradleConnectorService::class.java, connectorService, disposable)
  }

  private fun mockProjectConnection(execute: () -> Unit): ProjectConnection {
    val buildEnvironment = DefaultBuildEnvironment(
      DefaultBuildIdentifier(Path.of(projectPath)),
      DefaultGradleEnvironment(null, gradleVersion),
      DefaultJavaEnvironment(Path.of(gradleFixture.gradleJvmFixture.gradleJvmPath), emptyList())
    )
    val modelBuilder = mock<ModelBuilder<BuildEnvironment>>().apply {
      whenever(get()).thenReturn(buildEnvironment)
    }
    val buildLauncher = mock<BuildLauncher>().apply {
      whenever(run()).thenAnswer { execute() }
    }
    return mock<ProjectConnection>().apply {
      whenever(model(BuildEnvironment::class.java)).thenReturn(modelBuilder)
      whenever(newBuild()).thenReturn(buildLauncher)
    }
  }

  class DefaultBuildEnvironment(
    private val buildIdentifier: BuildIdentifier,
    private val gradle: GradleEnvironment,
    private val java: JavaEnvironment,
  ) : BuildEnvironment {
    override fun getBuildIdentifier(): BuildIdentifier = buildIdentifier
    override fun getGradle(): GradleEnvironment = gradle
    override fun getJava(): JavaEnvironment = java
  }

  private class DefaultBuildIdentifier(
    private val rootDir: Path,
  ) : BuildIdentifier {
    override fun getRootDir(): File = rootDir.toFile()
  }

  private class DefaultGradleEnvironment(
    private val gradleUserHome: Path?,
    private val gradleVersion: GradleVersion,
  ) : GradleEnvironment {
    override fun getGradleUserHome(): File? = gradleUserHome?.toFile()
    override fun getGradleVersion(): String = gradleVersion.version
  }

  private class DefaultJavaEnvironment(
    private val javaHome: Path,
    private val jvmArguments: List<String>,
  ) : JavaEnvironment {
    override fun getJavaHome(): File = javaHome.toFile()
    override fun getJvmArguments(): List<String> = jvmArguments
  }
}