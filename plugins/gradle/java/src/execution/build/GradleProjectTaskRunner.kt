// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.build.BuildViewManager
import com.intellij.compiler.impl.CompilerUtil
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.scratch.JavaScratchConfiguration
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.task.*
import com.intellij.task.TaskRunnerResults.FAILURE
import com.intellij.task.TaskRunnerResults.SUCCESS
import com.intellij.util.text.nullize
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.service.execution.loadHotswapDetectionInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.*
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Vladislav.Soroka
 */
class GradleProjectTaskRunner : ProjectTaskRunner() {

  override fun isFileGeneratedEventsSupported(): Boolean = true

  override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
    val resultPromise = AsyncPromise<Result>()
    val executionSettingsBuilder = TasksExecutionSettingsBuilder(project, *tasks)
    val rootPaths = executionSettingsBuilder.rootPaths
    if (rootPaths.isEmpty()) {
      LOG.warn("Nothing will be run for: " + tasks.contentToString())
      resultPromise.setResult(SUCCESS)
      return resultPromise
    }

    val successCounter = AtomicInteger()
    val errorCounter = AtomicInteger()

    val outputPathsFile = createTempOutputPathsFileIfNeeded(context)
    val taskCallback = object : TaskCallback {

      override fun onSuccess() = handle(true)
      override fun onFailure() = handle(false)

      fun handle(success: Boolean) {
        val successes = if (success) successCounter.incrementAndGet() else successCounter.get()
        val errors = if (success) errorCounter.get() else errorCounter.incrementAndGet()
        if (successes + errors == rootPaths.size) {
          if (!project.isDisposed()) {
            try {
              if (GradleImprovedHotswapDetection.isEnabled()) {
                GradleImprovedHotswapDetection.processInitScriptOutput(context, outputPathsFile)
              }
              else {
                val affectedRoots = getAffectedOutputRoots(outputPathsFile, context, executionSettingsBuilder)
                if (!affectedRoots.isEmpty()) {
                  if (context.isCollectionOfGeneratedFilesEnabled) {
                    context.addDirtyOutputPathsProvider { affectedRoots }
                  }
                  // refresh on output roots is required in order for the order enumerator to see all roots via VFS
                  // have to refresh in case of errors too, because run configuration may be set to ignore errors
                  CompilerUtil.refreshOutputRoots(affectedRoots)
                }
              }
            }
            finally {
              if (outputPathsFile != null) {
                FileUtil.delete(outputPathsFile)
              }
            }
          }
          resultPromise.setResult(if (errors > 0) FAILURE else SUCCESS)
        }
        else {
          if (successes + errors > rootPaths.size) {
            LOG.error("Unexpected callback!")
          }
        }
      }
    }

    for (rootProjectPath in rootPaths) {
      if (!executionSettingsBuilder.containsTasksToExecuteFor(rootProjectPath)) {
        LOG.warn("Nothing will be run for: " + tasks.contentToString() + " at '" + rootProjectPath + "'")
        taskCallback.onSuccess()
        continue
      }

      if (outputPathsFile != null && context.isCollectionOfGeneratedFilesEnabled) {
        executionSettingsBuilder.addInitScripts(rootProjectPath, loadHotswapDetectionInitScript(
          GradleImprovedHotswapDetection.isEnabled(),
          FileUtil.toCanonicalPath(outputPathsFile.absolutePath)
        ))
      }

      val settings = executionSettingsBuilder.build(rootProjectPath)

      val userData = UserDataHolderBase().also {
        it.putUserData(PROGRESS_LISTENER_KEY, BuildViewManager::class.java)
        it.putUserData(VERSION_SPECIFIC_SCRIPTS_KEY, executionSettingsBuilder.getVersionedInitScripts(rootProjectPath))
        it.putUserData(INIT_SCRIPT_KEY, executionSettingsBuilder.getInitScript(rootProjectPath))
        it.putUserData(INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME)
      }

      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, SYSTEM_ID,
                                 taskCallback, IN_BACKGROUND_ASYNC, false, userData)
    }
    return resultPromise
  }

  private fun createTempOutputPathsFileIfNeeded(context: ProjectTaskContext): File? {
    if (context.isCollectionOfGeneratedFilesEnabled) {
      try {
        return FileUtil.createTempFile("output", ".paths", true)
      }
      catch (e: IOException) {
        LOG.warn("Can not create temp file to collect Gradle tasks output paths", e)
      }
    }
    return null
  }

  private fun getAffectedOutputRoots(
    outputPathsFile: File?,
    context: ProjectTaskContext,
    executionSettingsBuilder: TasksExecutionSettingsBuilder,
  ): Set<String> {
    if (outputPathsFile != null && context.isCollectionOfGeneratedFilesEnabled) {
      try {
        return FileUtil.loadLines(outputPathsFile)
          .mapNotNullTo(LinkedHashSet()) { it.trim().nullize() }
      }
      catch (e: IOException) {
        LOG.warn("Can not load temp file with collected Gradle tasks output paths", e)
      }
    }
    return CompilerPaths.getOutputPaths(executionSettingsBuilder.affectedModules.toTypedArray()).toSet()
  }

  override fun canRun(project: Project, projectTask: ProjectTask, context: ProjectTaskContext?): Boolean {
    if (context != null && context.runConfiguration is JavaScratchConfiguration) {
      return false
    }
    return canRun(projectTask)
  }

  override fun canRun(projectTask: ProjectTask): Boolean {
    if (projectTask is ModuleBuildTask) {
      return isDelegatedBuildEnabled(projectTask.module)
    }
    if (projectTask is BuildTask) {
      return GradleBuildTasksProvider.EP_NAME.findFirstSafe { it.isApplicable(projectTask) } != null
    }
    if (projectTask is ExecuteRunConfigurationTask) {
      val runProfile = projectTask.runProfile
      if (runProfile is ModuleBasedConfiguration<*, *>) {
        val module = runProfile.configurationModule.module
        if (!isDelegatedBuildEnabled(module)) {
          return false
        }
      }
      return GradleExecutionEnvironmentProvider.EP_NAME.findFirstSafe { it.isApplicable(projectTask) } != null
    }
    return false
  }

  private fun isDelegatedBuildEnabled(module: Module?): Boolean {
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    return externalProjectPath != null &&
           ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, module) &&
           GradleProjectSettings.isDelegatedBuildEnabled(module!!.getProject(), externalProjectPath)
  }

  override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? {
    val environmentProvider = GradleExecutionEnvironmentProvider.EP_NAME.findFirstSafe { it.isApplicable(task) }
    return environmentProvider?.createExecutionEnvironment(project, task, executor)
  }

  override fun createExecutionEnvironment(project: Project, vararg tasks: ProjectTask): ExecutionEnvironment? {
    var environment = super.createExecutionEnvironment(project, *tasks)
    if (environment != null) {
      return environment
    }
    val executionSettingsBuilder = TasksExecutionSettingsBuilder(project, *tasks)
    val rootProjectPath = executionSettingsBuilder.rootPaths.firstOrNull() ?: return null
    val settings = executionSettingsBuilder.build(rootProjectPath)

    environment = ExternalSystemUtil.createExecutionEnvironment(project, SYSTEM_ID, settings, DefaultRunExecutor.EXECUTOR_ID)
    if (environment == null) {
      LOG.warn("Execution environment for $SYSTEM_ID is null")
      return null
    }

    (environment.runnerAndConfigurationSettings!!.configuration as UserDataHolder).also {
      it.putUserData(VERSION_SPECIFIC_SCRIPTS_KEY, executionSettingsBuilder.getVersionedInitScripts(rootProjectPath))
      it.putUserData(INIT_SCRIPT_KEY, executionSettingsBuilder.getInitScript(rootProjectPath))
      it.putUserData(INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME)
    }
    return environment
  }

  companion object {
    private val LOG = Logger.getInstance(GradleProjectTaskRunner::class.java)

    private const val BUILD_INIT_SCRIPT_NAME = "ijJvmBuildInit"
  }
}
