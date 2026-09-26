// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService

class KotlinConfiguratorExternalSystemSyncListener : ExternalSystemTaskNotificationListener {
    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        KotlinProjectConfigurationService.getInstance(project).onSyncStarted()

        // Removes the Kotlin not configured notification immediately when a project sync was started
        KotlinProjectConfigurationService.getInstance(project).refreshEditorNotifications()
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        val configurationService = KotlinProjectConfigurationService.getInstance(project)
        configurationService.onSyncFinished()
    }
}