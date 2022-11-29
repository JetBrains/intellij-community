// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * For now, relies on projects exposing and properly configuring the
 * "publish"-task.
 *
 * It is highly recommended to not use 'mavenLocal' as target for
 * publishing.
 */
object GradleProjectsPublishingService {

    fun publishSubproject(subprojectName: String, importedProjectRoot: Path, importedProject: Project) {
        val subprojectDirectory = importedProjectRoot.resolve(subprojectName)
        require(subprojectDirectory.exists()) { "Can't find subproject $subprojectName, checked at ${subprojectDirectory.toAbsolutePath()}" }

        runTaskAndGetErrorOutput(subprojectDirectory.toString(), importedProject, "publish")
    }

    private fun runTaskAndGetErrorOutput(projectPath: String, project: Project, taskName: String, scriptParameters: String = ""): String {
        val taskErrOutput = StringBuilder()
        val stdErrListener = object : ExternalSystemTaskNotificationListenerAdapter() {
            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
                if (!stdOut) {
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
            val taskCallback = object : TaskCallback {
                override fun onSuccess() {
                    future.complete(taskErrOutput.toString())
                }

                override fun onFailure() {
                    future.complete(taskErrOutput.toString())
                }
            }

            ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
                taskCallback,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC
            )

            return future.get(2, TimeUnit.MINUTES)
        }
        finally {
            notificationManager.removeNotificationListener(stdErrListener)
        }
    }
}
