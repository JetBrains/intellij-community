// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.scripting.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.projectRoots.JdkUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.*

val kotlinDslSyncListenerInstance: KotlinDslSyncListener?
    get() =
        ExternalSystemTaskNotificationListener.EP_NAME.findExtension(KotlinDslSyncListener::class.java)

class KotlinDslSyncListener : ExternalSystemTaskNotificationListener {
    companion object {
        val instance: KotlinDslSyncListener?
            get() =
                kotlinDslSyncListenerInstance
    }

    internal val tasks = WeakHashMap<ExternalSystemTaskId, KotlinDslGradleBuildSync>()

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        val task = KotlinDslGradleBuildSync(projectPath, id)
        synchronized(tasks) { tasks[id] = task }

        // project may be null in case of new project
        val project = id.findProject() ?: return
        task.projectId = id.ideProjectId
        GradleBuildRootsManager.getInstance(project)?.markImportingInProgress(projectPath)
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks.remove(id) } ?: return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        if (sync.gradleHome == null) {
            sync.gradleHome = GradleInstallationManager.getInstance()
                .getGradleHome(project, sync.workingDir)
                ?.path
        }

        if (sync.javaHome == null) {
            sync.javaHome = ExternalSystemApiUtil
                .getExecutionSettings<GradleExecutionSettings>(project, sync.workingDir, GradleConstants.SYSTEM_ID)
                .javaHome
        }

        sync.javaHome = sync.javaHome?.takeIf { JdkUtil.checkForJdk(it) }
            ?: run {
                // roll back to specified in GRADLE_JVM if for some reason sync.javaHome points to corrupted SDK
                val gradleJvm = GradleSettings.getInstance(project).getLinkedProjectSettings(sync.workingDir)?.gradleJvm
                try {
                    ExternalSystemJdkUtil.getJdk(project, gradleJvm)?.homePath
                } catch (_: Exception) {
                    null
                }
            }

        if (KotlinPluginModeProvider.isK1Mode()) {
            @Suppress("DEPRECATION")
            GradleScriptDefinitionsContributor.getInstance(project)?.reloadIfNeeded(
                sync.workingDir, sync.gradleHome, sync.javaHome
            )
        }

        saveScriptModels(project, sync)
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks[id] } ?: return
        sync.failed = true
    }

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks[id] } ?: return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        GradleBuildRootsManager.getInstance(project)?.markImportingInProgress(sync.workingDir, false)

        if (sync.failed) {
            reportErrors(project, sync)
        }
    }

    private fun ExternalSystemTaskId.isGradleRelatedTask() = projectSystemId == GradleConstants.SYSTEM_ID &&
            (type == RESOLVE_PROJECT /*|| type == EXECUTE_TASK*/)
}
