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
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

object GradleDaemonJvmCriteriaMigrationHelper {

    fun migrateToDaemonJvmCriteria(project: Project, externalProjectPath: String): CompletableFuture<Boolean> {
        return CoroutineScopeService.getCoroutineScope(project).async {
            migrateToDaemonJvmCriteriaImpl(project, externalProjectPath)
        }.asCompletableFuture()
    }

    private suspend fun migrateToDaemonJvmCriteriaImpl(project: Project, externalProjectPath: String): Boolean {
        val daemonJvmCriteria = resolveDaemonJvmCriteria(project, externalProjectPath)
        if (daemonJvmCriteria == null) {
            displayMigrationFailureMessage(project)
            return false
        }

        applyDefaultToolchainResolverPlugin(project, externalProjectPath)

        if (!GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, externalProjectPath, daemonJvmCriteria).await()) {
            displayMigrationFailureMessage(project)
            return false
        }

        overrideGradleJvmReferenceWithDefault(project, externalProjectPath)

        return true
    }

    private fun resolveDaemonJvmCriteria(project: Project, externalProjectPath: String): GradleDaemonJvmCriteria? {
        val gradleJvmPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, externalProjectPath) ?: return null
        val gradleJvmInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(gradleJvmPath) ?: return null
        return gradleJvmInfo.toJvmCriteria()
    }

    suspend fun applyDefaultToolchainResolverPlugin(project: Project, externalProjectPath: String) {
        if (!configureSettingsFileWithAppliedFoojayPlugin(project, externalProjectPath)) {
            createSettingsFileWithAppliedFoojayPlugin(project, externalProjectPath)
        }
    }

    private suspend fun configureSettingsFileWithAppliedFoojayPlugin(project: Project, externalProjectPath: String): Boolean {
        return writeCommandAction(project, GradleBundle.message("gradle.notifications.daemon.toolchain.migration.apply.plugin.command.name")) {
            val settingsFile = getTopLevelBuildScriptSettingsPsiFile(project, externalProjectPath) ?: return@writeCommandAction false
            val buildScriptSupport = GradleBuildScriptSupport.getManipulator(settingsFile)
            buildScriptSupport.addFoojayPlugin(settingsFile)
            return@writeCommandAction true
        }
    }

    private fun createSettingsFileWithAppliedFoojayPlugin(project: Project, externalProjectPath: String) {
        val settings = GradleSettings.getInstance(project)
        val projectSettings = settings.getLinkedProjectSettings(externalProjectPath) ?: return
        val gradleVersion = GradleInstallationManager.guessGradleVersion(projectSettings) ?: return
        createSettingsFileWithAppliedFoojayPlugin(Path.of(externalProjectPath), gradleVersion)
    }

    private fun createSettingsFileWithAppliedFoojayPlugin(projectPath: Path, gradleVersion: GradleVersion) {
        val gradleDsl = GradleDsl.valueOf(projectPath.resolve(KOTLIN_DSL_SCRIPT_NAME).exists())
        val settingsScriptName = GradleSettingScriptBuilder.getSettingsScriptName(gradleDsl)
        val settingsScriptPath = projectPath.resolve(settingsScriptName)
        val settingsScriptContent = settingsScript(gradleVersion, gradleDsl) {
            setProjectName(projectPath.name)
            withFoojayPlugin()
        }
        settingsScriptPath.writeText(settingsScriptContent)
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