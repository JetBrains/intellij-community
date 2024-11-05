// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

private val MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VERSION_CRITERIA: GradleVersion = GradleVersion.version("8.8")
private val MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VENDOR_CRITERIA: GradleVersion = GradleVersion.version("8.10")
private val MIN_VERSION_REQUIRING_DAEMON_TOOLCHAIN_CRITERIA: GradleVersion = GradleVersion.version("10.0") // Expected version

private const val UPDATE_DAEMON_JVM_TASK_VERSION_OPTION= "--jvm-version"
private const val UPDATE_DAEMON_JVM_TASK_VENDOR_OPTION= "--jvm-vendor"

object GradleDaemonJvmHelper {

  @JvmStatic
  fun isDaemonJvmCriteriaSupported(gradleVersion: GradleVersion) = gradleVersion >= MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VERSION_CRITERIA

  @JvmStatic
  fun isDamonJvmVendorCriteriaSupported(gradleVersion: GradleVersion) = gradleVersion >= MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VENDOR_CRITERIA

  @JvmStatic
  fun isDaemonJvmCriteriaRequired(gradleVersion: GradleVersion) = false // TODO replace with gradleVersion >= MIN_VERSION_REQUIRING_DAEMON_TOOLCHAIN_CRITERIA

  @JvmStatic
  fun isProjectUsingDaemonJvmCriteria(projectSettings: GradleProjectSettings): Boolean {
    val gradleVersion = projectSettings.resolveGradleVersion()
    val externalProjectPath = Path.of(projectSettings.externalProjectPath)
    return isProjectUsingDaemonJvmCriteria(externalProjectPath, gradleVersion)
  }

  @JvmStatic
  fun isProjectUsingDaemonJvmCriteria(externalProjectPath: Path, gradleVersion: GradleVersion) =
    Registry.`is`("gradle.daemon.jvm.criteria") && when {
      isDaemonJvmCriteriaRequired(gradleVersion) -> true
      isDaemonJvmCriteriaSupported(gradleVersion) && GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)?.version != null -> true
      else -> false
    }

  @JvmStatic
  @JvmOverloads
  fun updateProjectDaemonJvmCriteria(
    project: Project,
    externalProjectPath: String,
    version: String? = null,
    vendor: String? = null,
    executionMode: ProgressExecutionMode = ProgressExecutionMode.START_IN_FOREGROUND_ASYNC
  ) {
    val taskSettings = ExternalSystemTaskExecutionSettings().apply {
      this.externalProjectPath = externalProjectPath
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
      taskNames = mutableListOf(DaemonJvmPropertiesConfigurator.TASK_NAME).apply {
        version?.let { add("$UPDATE_DAEMON_JVM_TASK_VERSION_OPTION=$it") }
        vendor?.let { add("$UPDATE_DAEMON_JVM_TASK_VENDOR_OPTION=$it") }
      }
    }

    create(project, GradleConstants.SYSTEM_ID, DefaultRunExecutor.EXECUTOR_ID, taskSettings)
      .withProgressExecutionMode(executionMode)
      .withActivateToolWindowBeforeRun(true)
      .build()
      .also { ExternalSystemUtil.runTask(it) }
  }
}