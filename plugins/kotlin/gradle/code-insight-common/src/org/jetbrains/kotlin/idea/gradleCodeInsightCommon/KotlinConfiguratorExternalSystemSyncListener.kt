// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project


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
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        KotlinConfiguratorGradleSyncStateHolder.getInstance(project).gradleSyncInProgress = false
    }
}