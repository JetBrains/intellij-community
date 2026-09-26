// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.gradle.java.performance.dto.GradleTaskInfoDto
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.await
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.plugins.gradle.execution.GradleRunAnythingProvider
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * The command executes a Gradle task by name
 * Argument is serialized [com.intellij.gradle.java.performance.dto.GradleTaskInfoDto] as json
 * runAnything false - executes like double click form lifecycle
 * runAnything true - executes like double control and execute gradle [task]
 */
class ExecuteGradleTaskCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "executeGradleTask"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  private fun executeTaskFromRunAnything(project: Project, taskData: TaskData) {
    val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .build()
    GradleRunAnythingProvider().execute(context, "gradle ${taskData.name}")

  }

  private fun executeTaskFromLifecycle(project: Project,
                                       taskData: TaskData) {
    val taskExecutionInfo = ExternalSystemActionUtil.buildTaskInfo(taskData)
    ExternalSystemUtil.runTask(taskExecutionInfo.settings, taskExecutionInfo.executorId, project, GradleConstants.SYSTEM_ID,
                               null,
                               ProgressExecutionMode.NO_PROGRESS_ASYNC)
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val taskInfo = deserializeOptionsFromJson(extractCommandArgument(PREFIX), GradleTaskInfoDto::class.java)
    val settings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkedProjectsSettings
    val projectPath = taskInfo.projectHomeDirName?.let {
      settings.first { e -> e.externalProjectPath.contains(it) }.externalProjectPath
    } ?: project.getBasePath()!!
    val actionCallback = ActionCallback()
    project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        if (cause != null) {
          actionCallback.reject(cause.toString())
        }
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        if (exitCode != 0) {
          actionCallback.reject("Process finished with exit code $exitCode")
        }
        else {
          actionCallback.setDone()
        }
      }
    })

    val task = TaskData(GradleConstants.SYSTEM_ID, taskInfo.taskName, projectPath, "Description")
    if (taskInfo.runFromRunAnything)
      executeTaskFromRunAnything(project, task)
    else
      executeTaskFromLifecycle(project, task)
    actionCallback.await()
  }

  override fun getName(): String {
    return NAME
  }
}
