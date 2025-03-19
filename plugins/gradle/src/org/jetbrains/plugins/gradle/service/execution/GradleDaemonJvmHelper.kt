// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

private val MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VERSION_CRITERIA: GradleVersion = GradleVersion.version("8.8")
private val MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VENDOR_CRITERIA: GradleVersion = GradleVersion.version("8.10")
private val MIN_VERSION_REQUIRING_DAEMON_TOOLCHAIN_CRITERIA: GradleVersion = GradleVersion.version("10.0") // Expected version

private const val UPDATE_DAEMON_JVM_TASK_VERSION_OPTION= "--jvm-version"
private const val UPDATE_DAEMON_JVM_TASK_VENDOR_OPTION= "--jvm-vendor"

private val UPDATE_DAEMON_JVM_TASK = Key.create<Boolean>("UPDATE_DAEMON_JVM_TASK")

@ApiStatus.Internal
object GradleDaemonJvmHelper {

  @JvmStatic
  fun isDaemonJvmCriteriaSupported(gradleVersion: GradleVersion): Boolean {
    return gradleVersion >= MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VERSION_CRITERIA
  }

  @JvmStatic
  fun isDamonJvmVendorCriteriaSupported(gradleVersion: GradleVersion): Boolean {
    return gradleVersion >= MIN_VERSION_SUPPORTING_DAEMON_TOOLCHAIN_VENDOR_CRITERIA
  }

  @JvmStatic
  fun isDaemonJvmCriteriaRequired(gradleVersion: GradleVersion): Boolean {
    return false // TODO replace with gradleVersion >= MIN_VERSION_REQUIRING_DAEMON_TOOLCHAIN_CRITERIA
  }

  @JvmStatic
  fun isProjectUsingDaemonJvmCriteria(projectSettings: GradleProjectSettings): Boolean {
    val gradleVersion = projectSettings.resolveGradleVersion()
    val externalProjectPath = Path.of(projectSettings.externalProjectPath)
    return isProjectUsingDaemonJvmCriteria(externalProjectPath, gradleVersion)
  }

  @JvmStatic
  fun isProjectUsingDaemonJvmCriteria(externalProjectPath: Path, gradleVersion: GradleVersion): Boolean {
    return Registry.`is`("gradle.daemon.jvm.criteria") && when {
      isDaemonJvmCriteriaRequired(gradleVersion) -> true
      isDaemonJvmCriteriaSupported(gradleVersion) && GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)?.version != null -> true
      else -> false
    }
  }

  @JvmStatic
  fun isExecutingUpdateDaemonJvmTask(settings: GradleExecutionSettings): Boolean {
    return settings.getUserData(UPDATE_DAEMON_JVM_TASK) == true
  }

  @JvmStatic
  fun getGradleJvmForUpdateDaemonJvmTask(id: ExternalSystemTaskId): String? {
    val project = id.findProject() ?: return null
    val projectRootManager = ProjectRootManager.getInstance(project)
    return projectRootManager.projectSdk?.homePath
  }

  @JvmStatic
  fun updateProjectDaemonJvmCriteria(
    project: Project,
    externalProjectPath: String,
    daemonJvmCriteria: GradleDaemonJvmCriteria
  ): CompletableFuture<Boolean> {
    val taskSettings = ExternalSystemTaskExecutionSettings().apply {
      this.externalProjectPath = externalProjectPath
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
      executionName = GradleBundle.message("gradle.execution.name.config.daemon.jvm.criteria")
      taskNames = buildList {
        add(DaemonJvmPropertiesConfigurator.TASK_NAME)
        val version = daemonJvmCriteria.version
        if (version != null) {
          add(UPDATE_DAEMON_JVM_TASK_VERSION_OPTION)
          add(StringUtil.wrapWithDoubleQuote(version))
        }
        val vendor = daemonJvmCriteria.vendor
        if (vendor != null) {
          add(UPDATE_DAEMON_JVM_TASK_VENDOR_OPTION)
          add(StringUtil.wrapWithDoubleQuote(vendor.rawVendor))
        }
      }
    }

    val taskUserData = UserDataHolderBase().apply {
      putUserData(UPDATE_DAEMON_JVM_TASK, true)
    }

    val taskResult = CompletableFuture<Boolean>()

    val taskCallback = object : TaskCallback {
      override fun onSuccess() {
        taskResult.complete(true)
      }

      override fun onFailure() {
        taskResult.complete(false)
      }
    }

    val executionSpec = TaskExecutionSpec.create(project, GradleConstants.SYSTEM_ID, DefaultRunExecutor.EXECUTOR_ID, taskSettings)
      .withUserData(taskUserData)
      .withCallback(taskCallback)
      .build()

    ExternalSystemUtil.runTask(executionSpec)

    return taskResult
  }
}