// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class GradleTasksExecutionTestCase : GradleImportingTestCase() {

  protected fun runTaskAndGetErrorOutput(projectPath: String, taskName: String, scriptParameters: String = ""): String {
    val taskErrOutput = StringBuilder()
    val stdErrListener = object : ExternalSystemTaskNotificationListener {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        if (processOutputType.isStderr) {
          taskErrOutput.append(text)
        }
      }
    }
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(stdErrListener)
    try {
      val settings = ExternalSystemTaskExecutionSettings()
      settings.externalProjectPath = projectPath
      settings.taskNames = listOf(taskName)
      settings.scriptParameters = scriptParameters
      settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id

      val future = CompletableFuture<String>()
      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID,
                                 object : TaskCallback {
                                   override fun onSuccess() {
                                     future.complete(taskErrOutput.toString())
                                   }

                                   override fun onFailure() {
                                     future.complete(taskErrOutput.toString())
                                   }
                                 }, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      return future.get(10, TimeUnit.SECONDS)
    }
    finally {
      notificationManager.removeNotificationListener(stdErrListener)
    }
  }

  override fun assumeGradleVersion() {}
}
