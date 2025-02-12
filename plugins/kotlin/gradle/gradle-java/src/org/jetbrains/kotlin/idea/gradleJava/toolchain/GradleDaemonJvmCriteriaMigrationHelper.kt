// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.toJvmVendor
import java.util.concurrent.CompletableFuture

object GradleDaemonJvmCriteriaMigrationHelper {

    fun migrateToDaemonJvmCriteria(project: Project, externalProjectPath: String): CompletableFuture<Boolean> {
        return CoroutineScopeService.getCoroutineScope(project).async {
            migrateToDaemonJvmCriteriaImpl(project, externalProjectPath)
        }.asCompletableFuture()
    }

    private suspend fun migrateToDaemonJvmCriteriaImpl(project: Project, externalProjectPath: String): Boolean {
        val gradleJvmPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, externalProjectPath)
        if (gradleJvmPath == null) {
            displayMigrationFailureMessage(project)
            return false
        }
        val gradleJvmInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(gradleJvmPath)
        if (gradleJvmInfo == null) {
            displayMigrationFailureMessage(project)
            return false
        }
        val gradleJvmCriteria = GradleDaemonJvmCriteria(
            version = gradleJvmInfo.version.feature.toString(),
            vendor = gradleJvmInfo.variant.toJvmVendor()
        )

        applyDefaultToolchainResolverPlugin(project)

        val isSuccess = GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, externalProjectPath, gradleJvmCriteria)
            .await()

        if (!isSuccess) {
            displayMigrationFailureMessage(project)
            return false
        }

        overrideGradleJvmReferenceWithDefault(project, externalProjectPath)
        return true
    }

    suspend fun applyDefaultToolchainResolverPlugin(project: Project) {
        writeCommandAction(project, GradleBundle.message("gradle.notifications.daemon.toolchain.migration.apply.plugin.command.name")) {
            project.getTopLevelBuildScriptSettingsPsiFile()?.let { topLevelBuildScript ->
                val buildScriptSupport = GradleBuildScriptSupport.getManipulator(topLevelBuildScript)
                buildScriptSupport.addFoojayPlugin(topLevelBuildScript)
            } ?: run {
                // TODO create settings.gradle file with Foojay Plugin
            }
        }
    }

    private fun overrideGradleJvmReferenceWithDefault(project: Project, externalProjectPath: String) {
        val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath)
        projectSettings?.gradleJvm = USE_PROJECT_JDK
    }

    private suspend fun displayMigrationFailureMessage(project: Project) {
        withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(
                project,
                GradleBundle.message("gradle.notifications.daemon.toolchain.migration.error.message", project.name),
                GradleBundle.message("gradle.notifications.daemon.toolchain.migration.error.title")
            )
        }
    }

    @Service(Service.Level.PROJECT)
    private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
        companion object {
            fun getCoroutineScope(project: Project): CoroutineScope {
                return project.service<CoroutineScopeService>().coroutineScope
            }
        }
    }
}