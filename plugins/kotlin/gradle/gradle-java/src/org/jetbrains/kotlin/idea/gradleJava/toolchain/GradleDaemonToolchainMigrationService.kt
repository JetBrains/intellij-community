// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.kotlin.idea.gradle.extensions.nameSupportedByFoojayPlugin
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle

@Service(Service.Level.PROJECT)
class GradleDaemonToolchainMigrationService(private val project: Project) {

    fun startMigration(externalProjectPath: String) {
        val currentJdkInfo = GradleInstallationManager.getInstance().getGradleJvmPath(project, externalProjectPath)?.let {
            JdkVersionDetector.getInstance().detectJdkVersionInfo(it)
        }
        if (currentJdkInfo?.version == null) {
            displayMigrationFailureMessage()
            return
        }

        applyDefaultToolchainResolverPlugin()

        GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(
            project,
            externalProjectPath,
            GradleDaemonJvmCriteria(
                version = currentJdkInfo.version.feature.toString(),
                vendor = currentJdkInfo.variant.nameSupportedByFoojayPlugin
            ), object : TaskCallback {
                override fun onSuccess() {
                    overrideGradleJvmReferenceWithDefault(externalProjectPath)
                }

                override fun onFailure() {
                    displayMigrationFailureMessage()
                }
            }
        )
    }

    private fun applyDefaultToolchainResolverPlugin() {
        CommandProcessor.getInstance().executeCommand(project, {
            ApplicationManager.getApplication().runWriteAction {
                project.getTopLevelBuildScriptSettingsPsiFile()?.let {
                    val buildScriptSupport = GradleBuildScriptSupport.getManipulator(it)
                    buildScriptSupport.addFoojayPlugin(it)
                }
            }
        }, GradleBundle.message("gradle.notifications.daemon.toolchain.migration.apply.plugin.command.name"), null)
    }

    private fun overrideGradleJvmReferenceWithDefault(externalProjectPath: String) {
        val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath)
        projectSettings?.gradleJvm = USE_PROJECT_JDK
    }

    private fun displayMigrationFailureMessage() {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(
                project,
                GradleBundle.message("gradle.notifications.daemon.toolchain.migration.error.message", project.name),
                GradleBundle.message("gradle.notifications.daemon.toolchain.migration.error.title")
            )
        }
    }
}