// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.externalSystem.service.notification.callback.OpenExternalSystemSettingsCallback
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathMapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.gradle.issue.IncorrectGradleJdkIssue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleBundle.PATH_TO_BUNDLE
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import org.jetbrains.plugins.gradle.util.getGradleJvmLookupProvider
import org.jetbrains.plugins.gradle.util.nonblockingResolveGradleJvmInfo
import java.io.File

@ApiStatus.Internal
class LocalGradleExecutionAware : GradleExecutionAware {
  override fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ) {
    if (!isPreviewMode) prepareJvmForExecution(task, externalProjectPath, taskNotificationListener, project)
  }

  override fun getEnvironmentConfigurationProvider(runConfiguration: ExternalSystemRunConfiguration,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    val targetEnvironmentConfiguration = getEnvironmentConfiguration(runConfiguration, project) ?: return null
    return GradleEnvironmentConfigurationProvider(targetEnvironmentConfiguration)
  }

  override fun getEnvironmentConfigurationProvider(projectPath: String,
                                                   isPreviewMode: Boolean,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    val targetEnvironmentConfiguration = localEnvironment() ?: return null
    return GradleEnvironmentConfigurationProvider(targetEnvironmentConfiguration)
  }

  override fun getDefaultBuildLayoutParameters(project: Project): BuildLayoutParameters = LocalBuildLayoutParameters(project, null)

  override fun getBuildLayoutParameters(project: Project, projectPath: String): BuildLayoutParameters =
    LocalBuildLayoutParameters(project, projectPath)

  override fun isGradleInstallationHomeDir(project: Project, homePath: String): Boolean {
    val libs = File(homePath, "lib")
    if (!libs.isDirectory) {
      if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
        LOG.info("Gradle sdk check failed for the path '$homePath'. Reason: it doesn't have a child directory named 'lib'")
      }
      return false
    }
    val found = findGradleJar(libs.listFiles()) != null
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      LOG.info("Gradle home check ${if (found) "passed" else "failed"} for the path '$homePath'")
    }
    return found
  }

  @ApiStatus.Internal
  fun prepareJvmForExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ): SdkInfo? {
    val settings = project.lock { GradleSettings.getInstance(it) }
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath) ?: return null
    // Projects using Daemon JVM criteria with a compatible Gradle version will skip any
    // Gradle JDK configuration validation since this will be delegated to Gradle
    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectSettings)) return null

    val originalGradleJvm = projectSettings.gradleJvm
    val provider = project.lock { getGradleJvmLookupProvider(it, projectSettings) }
    var sdkInfo = project.lock { provider.nonblockingResolveGradleJvmInfo(it, projectSettings.externalProjectPath, originalGradleJvm) }
    if (sdkInfo is SdkInfo.Undefined || sdkInfo is SdkInfo.Unresolved || sdkInfo is SdkInfo.Resolving) {
      waitForGradleJvmResolving(provider, task, taskNotificationListener)
      if (projectSettings.gradleJvm == null) {
        projectSettings.gradleJvm = originalGradleJvm ?: ExternalSystemJdkUtil.USE_PROJECT_JDK
      }
      sdkInfo = project.lock { provider.nonblockingResolveGradleJvmInfo(it, projectSettings.externalProjectPath, projectSettings.gradleJvm) }
    }

    val gradleJvm = projectSettings.gradleJvm
    if (sdkInfo !is SdkInfo.Resolved) {
      LOG.warn("Gradle JVM ($gradleJvm) isn't resolved: $sdkInfo")
      throw jdkConfigurationException("gradle.jvm.is.invalid")
    }
    val homePath = sdkInfo.homePath ?: run {
      LOG.warn("No Gradle JVM ($gradleJvm) home path: $sdkInfo")
      throw jdkConfigurationException("gradle.jvm.is.invalid")
    }
    checkForWslJdkOnWindows(homePath, projectSettings.externalProjectPath, task)
    if (!JdkUtil.checkForJdk(homePath)) {
      LOG.warn("Invalid Gradle JVM ($gradleJvm) home path: $sdkInfo")
      throw jdkConfigurationException("gradle.jvm.is.invalid")
    }
    if (!JdkUtil.checkForJre(homePath)) {
      LOG.warn("Gradle JVM ($gradleJvm) is JRE instead JDK: $sdkInfo")
      throw jdkConfigurationException("gradle.jvm.is.jre")
    }
    return sdkInfo
  }

  private fun checkForWslJdkOnWindows(homePath: String, externalProjectPath: String, task: ExternalSystemTask) {
    if (WSLUtil.isSystemCompatible() &&
        WslPath.isWslUncPath(homePath) &&
        !WslPath.isWslUncPath(externalProjectPath)) {
      val isResolveProjectTask = task is ExternalSystemResolveProjectTask
      val message = GradleBundle.message("gradle.incorrect.jvm.wslJdk.on.win.issue.description")
      throw BuildIssueException(IncorrectGradleJdkIssue(externalProjectPath, homePath, message, isResolveProjectTask))
    }
  }

  private class GradleEnvironmentConfigurationProvider(targetEnvironmentConfiguration: TargetEnvironmentConfiguration) : GradleServerConfigurationProvider {
    override val environmentConfiguration = targetEnvironmentConfiguration
    override val pathMapper: PathMapper? = null
  }

  private fun getEnvironmentConfiguration(runConfiguration: ExternalSystemRunConfiguration,
                                          project: Project): TargetEnvironmentConfiguration? {
    val gradleRunConfiguration = runConfiguration as? GradleRunConfiguration ?: return null
    val targetName = gradleRunConfiguration.options.remoteTarget ?: return localEnvironment()
    return TargetEnvironmentsManager.getInstance(project).targets.findByName(targetName) ?: return localEnvironment()
  }

  private fun localEnvironment(): TargetEnvironmentConfiguration? =
    if (Registry.`is`("gradle.tooling.use.external.process", false))
      object : TargetEnvironmentConfiguration(LOCAL_TARGET_TYPE_ID) {
        override var projectRootOnTarget: String = ""
      }
    else null

  /**
   * Critical execution section.
   * An explicit WriteAction is required to prevent the project from disposing.
   */
  private fun <R> Project.lock(action: (Project) -> R): R {
    return invokeAndWaitIfNeeded {
      runWriteAction {
        when (isDisposed) {
          true -> throw ProcessCanceledException()
          else -> action(this)
        }
      }
    }
  }

  private fun jdkConfigurationException(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String): ExternalSystemJdkException {
    val errorMessage = GradleBundle.message(key)
    val openSettingsMessage = GradleBundle.message("gradle.open.gradle.settings")
    val exceptionMessage = String.format("$errorMessage <a href='%s'>$openSettingsMessage</a> \n", OpenExternalSystemSettingsCallback.ID)
    return ExternalSystemJdkException(exceptionMessage, null, OpenExternalSystemSettingsCallback.ID)
  }

  private fun waitForGradleJvmResolving(
    lookupProvider: SdkLookupProvider,
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener
  ) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      LOG.error("Do not perform synchronous wait for sdk downloading in EDT - causes deadlock.")
      throw jdkConfigurationException("gradle.jvm.is.being.resolved.error")
    }

    val progressIndicator = lookupProvider.progressIndicator ?: ProgressIndicatorBase()
    submitProgressStarted(task, taskNotificationListener, progressIndicator, progressIndicator, GradleBundle.message("gradle.jvm.is.being.resolved"))
    ProgressIndicatorListener.whenProgressFractionChanged(progressIndicator) {
      submitProgressStatus(task, taskNotificationListener, progressIndicator, progressIndicator, GradleBundle.message("gradle.jvm.is.being.resolved"))
    }
    whenTaskCanceled(task) { progressIndicator.cancel() }
    lookupProvider.waitForLookup()
    submitProgressFinished(task, taskNotificationListener, progressIndicator, progressIndicator, GradleBundle.message("gradle.jvm.has.been.resolved"))
  }


  private fun findGradleJar(files: Array<File?>?): File? {
    if (files == null) return null

    for (file in files) {
      if (GradleInstallationManager.GRADLE_JAR_FILE_PATTERN.matcher(file!!.name).matches()) {
        return file
      }
    }
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      val filesInfo = StringBuilder()
      for (file in files) {
        filesInfo.append(file!!.absolutePath).append(';')
      }
      if (filesInfo.isNotEmpty()) {
        filesInfo.setLength(filesInfo.length - 1)
      }
      LOG.info("Gradle sdk check fails. " +
               "Reason: no one of the given files matches gradle JAR pattern (${GradleInstallationManager.GRADLE_JAR_FILE_PATTERN}). " +
               "Files: $filesInfo")
    }
    return null
  }

  companion object {
    private val LOG = logger<LocalGradleExecutionAware>()
    const val LOCAL_TARGET_TYPE_ID = "local"
  }
}