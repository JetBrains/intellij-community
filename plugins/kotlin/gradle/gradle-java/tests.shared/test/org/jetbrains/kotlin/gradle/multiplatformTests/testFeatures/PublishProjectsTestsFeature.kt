// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Allows running `publish`-tasks in the designated subprojects before import
 *
 * Subprojects should configure publishing manually
 *
 * Recommended configuration, suitable for most cases:
 * ```
 * // build.gradle.kts
 * plugins {
 *     id("maven-publish")
 * }
 *
 * group = "org.jetbrains.kotlin.mpp.tests"
 * version = "1.0"
 *
 * publishing {
 *     repositories {
 *         maven("$rootDir/repo")
 *     }
 * }
 * ```
 *
 * If you want to publish MPP with Android-target, don't forget to
 * define published variants:
 * ```
 * kotlin {
 *     android {
 *         publishLibraryVariants("release", "debug")
 *     }
 *     ...
 * }
 * ```
 */
interface GradleProjectsPublishingDsl {
    fun TestConfigurationDslScope.publish(vararg subprojectNames: String) {
        writeAccess.getConfiguration(GradleProjectsPublishingTestsFeature)
            .publishedSubprojectNames.addAll(subprojectNames)
    }
}

object GradleProjectsPublishingTestsFeature : TestFeature<ProjectsToPublish> {
    override fun createDefaultConfiguration(): ProjectsToPublish = ProjectsToPublish(mutableSetOf())

    override fun KotlinSyncTestsContext.beforeImport() {
        testConfiguration.getConfiguration(this@GradleProjectsPublishingTestsFeature).publishedSubprojectNames.forEach {
            GradleProjectsPublisher.publishSubproject(it, testProjectRoot, testProject)
        }
    }
}

class ProjectsToPublish(val publishedSubprojectNames: MutableSet<String>)


object GradleProjectsPublisher {

    fun publishSubproject(subprojectName: String, importedProjectRoot: File, importedProject: Project) {
        val subprojectDirectory = importedProjectRoot.resolve(subprojectName)
        require(subprojectDirectory.exists()) { "Can't find subproject $subprojectName, checked at ${subprojectDirectory.canonicalPath}" }
        requireNotNull(subprojectDirectory.walk().firstOrNull { it.isFile && it.extension == "kt" }) {
            "Can't publish subproject $subprojectName because it doesn't have any .kt-sources"
        }

        val output = runTaskAndGetErrorOutput(subprojectDirectory.toString(), importedProject, "publish")
        if (output.isNotEmpty()) {
            error("Unexpected errors while running 'publish' task in $subprojectName\n\n$output")
        }
    }

    private fun runTaskAndGetErrorOutput(projectPath: String, project: Project, taskName: String, scriptParameters: String = ""): String {
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
