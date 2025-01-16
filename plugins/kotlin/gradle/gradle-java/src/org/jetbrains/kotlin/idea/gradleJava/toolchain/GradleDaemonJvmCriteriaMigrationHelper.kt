// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.service.project.wizard.AbstractGradleModuleBuilder
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

object GradleDaemonJvmCriteriaMigrationHelper {

    fun migrateToDaemonJvmCriteria(project: Project, externalProjectPath: String): CompletableFuture<Boolean> {
        return CoroutineScopeService.getCoroutineScope(project).async {
            migrateToDaemonJvmCriteriaImpl(project, externalProjectPath)
        }.asCompletableFuture()
    }

    private suspend fun migrateToDaemonJvmCriteriaImpl(project: Project, externalProjectPath: String): Boolean {
        val isSuccess = GradleDaemonJvmHelper.setUpProjectDaemonJvmCriteria(project, externalProjectPath) {
            applyDefaultToolchainResolverPlugin(project)
        }.await()

        if (!isSuccess) {
            displayMigrationFailureMessage(project)
            return false
        }

        overrideGradleJvmReferenceWithDefault(project, externalProjectPath)
        return true
    }

    fun applyDefaultToolchainResolverPlugin(project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            project.executeWriteCommand(GradleBundle.message("gradle.notifications.daemon.toolchain.migration.apply.plugin.command.name")) {
                project.getTopLevelBuildScriptSettingsPsiFile()?.let { topLevelBuildScript ->
                    val buildScriptSupport = GradleBuildScriptSupport.getManipulator(topLevelBuildScript)
                    buildScriptSupport.addFoojayPlugin(topLevelBuildScript)
                } ?: run {
                    createBuildScriptSettingsFileWithAppliedFoojayPlugin(project)
                }
            }
        }
    }

    private fun overrideGradleJvmReferenceWithDefault(project: Project, externalProjectPath: String) {
        val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath)
        projectSettings?.gradleJvm = USE_PROJECT_JDK
    }

    private fun createBuildScriptSettingsFileWithAppliedFoojayPlugin(project: Project) {
        project.guessProjectDir()?.let { projectDir ->
            val projectPath = projectDir.toNioPath()
            val useKotlinDSL = projectPath.resolve(KOTLIN_DSL_SCRIPT_NAME).exists()
            val settingsScriptFile = AbstractGradleModuleBuilder.setupGradleSettingsFile(
                projectDir.toNioPath(), projectDir, project.name, null, false, useKotlinDSL
            )
            val settingsScriptBuilder = GradleSettingScriptBuilder.create(useKotlinDSL).apply {
                withFoojayPlugin()
            }
            VfsUtil.saveText(settingsScriptFile, settingsScriptBuilder.generate())
        }
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