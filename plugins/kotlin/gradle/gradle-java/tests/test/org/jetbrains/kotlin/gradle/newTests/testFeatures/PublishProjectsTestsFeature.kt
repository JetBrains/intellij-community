// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object GradleProjectsPublishingTestsFeature : TestFeature<ProjectsToPublish> {
    override fun createDefaultConfiguration(): ProjectsToPublish = ProjectsToPublish(mutableSetOf())

    override fun KotlinMppTestsContext.beforeImport() {
        testConfiguration.getConfiguration(this@GradleProjectsPublishingTestsFeature).publishedSubprojectNames.forEach {
            GradleProjectsPublisher.publishSubproject(it, testProjectRoot, testProject)
        }
    }
}

class ProjectsToPublish(val publishedSubprojectNames: MutableSet<String>)

interface GradleProjectsPublishingDsl {
    fun TestConfigurationDslScope.publish(vararg subprojectNames: String) {
        writeAccess.getConfiguration(GradleProjectsPublishingTestsFeature)
            .publishedSubprojectNames.addAll(subprojectNames)
    }
}

/**
 * For now, relies on projects exposing and properly configuring the
 * "publish"-task.
 *
 * It is highly recommended to not use 'mavenLocal' as target for
 * publishing.
 */
object GradleProjectsPublisher {

    fun publishSubproject(subprojectName: String, importedProjectRoot: File, importedProject: Project) {
        val subprojectDirectory = importedProjectRoot.resolve(subprojectName)
        require(subprojectDirectory.exists()) { "Can't find subproject $subprojectName, checked at ${subprojectDirectory.canonicalPath}" }

        val output = runTaskAndGetErrorOutput(subprojectDirectory.toString(), importedProject, "publish")
        if (output.isNotEmpty()) {
            error("Unexpected errors while running 'publish' task in $subprojectName\n\n$output")
        }
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
                    future.complete("")
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
