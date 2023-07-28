// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.util.isKotlinFileType

@Service(Service.Level.PROJECT)
internal class KotlinConfiguratorGradleSyncStateHolder {
    @Volatile
    internal var gradleSyncInProgress: Boolean = false

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinConfiguratorGradleSyncStateHolder {
            return project.service<KotlinConfiguratorGradleSyncStateHolder>()
        }
    }
}

internal fun Project.isGradleSyncInProgress(): Boolean {
    return KotlinConfiguratorGradleSyncStateHolder.getInstance(this).gradleSyncInProgress
}

class KotlinConfiguratorExternalSystemSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
    override fun onStart(id: ExternalSystemTaskId, workingDir: String) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        KotlinConfiguratorGradleSyncStateHolder.getInstance(project).gradleSyncInProgress = true

        // We want to remove the "Kotlin not configured" notification banner as fast as possible
        // once a gradle reload was started.
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val openKotlinFiles = openFiles.filter { it.isKotlinFileType() }
        if (openKotlinFiles.isEmpty()) return
        val editorNotifications = EditorNotifications.getInstance(project)

        openKotlinFiles.forEach {
            editorNotifications.updateNotifications(it)
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        KotlinConfiguratorGradleSyncStateHolder.getInstance(project).gradleSyncInProgress = false
    }
}