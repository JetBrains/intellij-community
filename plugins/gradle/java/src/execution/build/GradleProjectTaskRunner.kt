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
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.externalSystem.util.task.TaskExecutionUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.task.*
import com.intellij.task.TaskRunnerResults.SUCCESS
import com.intellij.util.text.nullize
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.plugins.gradle.GradleJavaCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.loadHotswapDetectionInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.*
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readLines

class GradleProjectTaskRunner : ProjectTaskRunner() {

  override fun isFileGeneratedEventsSupported(): Boolean = true

  /**
   * Only one project build task is allowed to be executed by Gradle at the same moment.
   * It is necessary to reduce the number of working Gradle daemons in parallel.
   *
   * The GradleProjectTaskRunner API allows running several project build tasks in a batch.
   * However, they should be provided as one run transaction.
   */
  private val mutex = Mutex()

  override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
    return project.gradleCoroutineScope.async<Result> {
      mutex.withLock {
        run(project, context, tasks.asList())
      }
      SUCCESS
    }.asCompletableFuture().asPromise()
  }

  private suspend fun run(project: Project, context: ProjectTaskContext, tasks: List<ProjectTask>) {
    val taskOutputEelPath = if (context.isCollectionOfGeneratedFilesEnabled) createTaskOutputFile(project) else null
    val taskOutputPath = taskOutputEelPath?.asNioPath()
    try {
      val hotswapDetectionInitScript = taskOutputEelPath?.let {
        loadHotswapDetectionInitScript(GradleImprovedHotswapDetection.isEnabled(), it)
      }

      val settings = TasksExecutionSettingsBuilder(tasks)

      if (settings.rootPaths.isEmpty()) {
        LOG.warn("Nothing will be run for $tasks")
        return
      }

      coroutineScope {
        for (rootProjectPath in settings.rootPaths) {
          val tasksToExecute = settings.getTasksToExecute(rootProjectPath)

          if (tasksToExecute.isEmpty()) {
            LOG.warn("Nothing will be run for $tasks at '$rootProjectPath'")
            continue
          }

          if (hotswapDetectionInitScript != null) {
            settings.addInitScripts(rootProjectPath, hotswapDetectionInitScript)
          }

          launch {
            TaskExecutionUtil.runTask(
              TaskExecutionSpec.create()
                .withProject(project)
                .withSystemId(SYSTEM_ID)
                .withSettings(ExternalSystemTaskExecutionSettings().also {
                  it.executionName = GradleBundle.message("gradle.execution.name.build.project.", Path(rootProjectPath).fileName)
                  it.externalSystemIdString = SYSTEM_ID.id
                  it.externalProjectPath = rootProjectPath
                  it.taskNames = tasksToExecute
                })
                .withUserData(UserDataHolderBase().also {
                  it.putUserData(PROGRESS_LISTENER_KEY, BuildViewManager::class.java)
                  it.putUserData(VERSION_SPECIFIC_SCRIPTS_KEY, settings.getVersionedInitScripts(rootProjectPath))
                  it.putUserData(INIT_SCRIPT_KEY, settings.getInitScript(rootProjectPath))
                  it.putUserData(INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME)
                })
            )
          }
        }
      }

      if (GradleImprovedHotswapDetection.isEnabled()) {
        GradleImprovedHotswapDetection.processInitScriptOutput(context, taskOutputPath)
      }
      else {
        val affectedRoots = getAffectedOutputRoots(settings, taskOutputPath)
        if (!affectedRoots.isEmpty()) {
          context.addDirtyOutputPathsProvider { affectedRoots }
        }
        // refresh on output roots is required in order for the order enumerator to see all roots via VFS
        // have to refresh in case of errors too, because run configuration may be set to ignore errors
        CompilerUtil.refreshOutputRoots(affectedRoots)
      }
    }
    finally {
      taskOutputPath?.deleteIfExists()
    }
  }

  private suspend fun createTaskOutputFile(project: Project): EelPath? {
    try {
      val eel = project.getEelDescriptor().upgrade()
      return eel.fs.createTemporaryFile()
        .prefix("output")
        .suffix(".paths")
        .deleteOnExit(true)
        .getOrThrow()
    }
    catch (e: IOException) {
      LOG.warn("Can not create temp file to collect Gradle tasks output paths", e)
    }
    return null
  }

  private fun getAffectedOutputRoots(settings: TasksExecutionSettingsBuilder, outputPathsFile: Path?): Set<String> {
    if (outputPathsFile != null) {
      try {
        return outputPathsFile.readLines().mapNotNullTo(LinkedHashSet()) { it.trim().nullize() }
      }
      catch (e: IOException) {
        LOG.warn("Can not load temp file with collected Gradle tasks output paths", e)
      }
    }
    return CompilerPaths.getOutputPaths(settings.affectedModules.toTypedArray()).toSet()
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
        if (!isDelegatedRunEnabled(module)) {
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

  private fun isDelegatedRunEnabled(module: Module?): Boolean {
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    return externalProjectPath != null &&
           ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, module) &&
           GradleProjectSettings.isDelegatedRunEnabled(module!!.getProject(), externalProjectPath)
  }

  override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? {
    val environmentProvider = GradleExecutionEnvironmentProvider.EP_NAME.findFirstSafe { it.isApplicable(task) }
    return environmentProvider?.createExecutionEnvironment(project, task, executor)
  }

  override fun createExecutionEnvironment(project: Project, vararg tasks: ProjectTask): ExecutionEnvironment? {
    return super.createExecutionEnvironment(project, *tasks)
           ?: createExecutionEnvironmentImpl(project, *tasks)
  }

  // Needed for Fleet FLEET-T-4855
  private fun createExecutionEnvironmentImpl(project: Project, vararg tasks: ProjectTask): ExecutionEnvironment? {
    val settingsBuilder = TasksExecutionSettingsBuilder(tasks.asList())

    val rootProjectPath = settingsBuilder.rootPaths.firstOrNull() ?: run {
      LOG.warn("Execution environment is null for $tasks")
      return null
    }

    val tasksToExecute = settingsBuilder.getTasksToExecute(rootProjectPath)

    if (tasksToExecute.isEmpty()) {
      LOG.warn("Execution environment is null for $tasks at '$rootProjectPath'")
      return null
    }

    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = GradleBundle.message("gradle.execution.name.build.project.", Path(rootProjectPath).fileName)
      it.externalSystemIdString = SYSTEM_ID.id
      it.externalProjectPath = rootProjectPath
      it.taskNames = tasksToExecute
    }

    val environment = ExternalSystemUtil.createExecutionEnvironment(project, SYSTEM_ID, settings, DefaultRunExecutor.EXECUTOR_ID) ?: run {
      LOG.warn("Execution environment for $SYSTEM_ID is null")
      return null
    }

    (environment.runnerAndConfigurationSettings!!.configuration as UserDataHolder).also {
      it.putUserData(PROGRESS_LISTENER_KEY, BuildViewManager::class.java)
      it.putUserData(VERSION_SPECIFIC_SCRIPTS_KEY, settingsBuilder.getVersionedInitScripts(rootProjectPath))
      it.putUserData(INIT_SCRIPT_KEY, settingsBuilder.getInitScript(rootProjectPath))
      it.putUserData(INIT_SCRIPT_PREFIX_KEY, BUILD_INIT_SCRIPT_NAME)
    }

    return environment
  }

  companion object {
    private val LOG = Logger.getInstance(GradleProjectTaskRunner::class.java)

    private const val BUILD_INIT_SCRIPT_NAME = "ijJvmBuildInit"
  }
}
