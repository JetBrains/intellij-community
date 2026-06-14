// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.plugins.gradle.util.GradleConstants

abstract class AbstractKotlinDslSyncListener : ExternalSystemTaskNotificationListener {
    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return
        val project = id.findProject() ?: return
        GradleBuildRootsLocator.getInstance(project).markImportingInProgress(projectPath)
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        GradleBuildRootsLocator.getInstance(project).markImportingInProgress(projectPath, false)
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        if (!id.isGradleRelatedTask()) return
        val project = id.findProject() ?: return
        GradleBuildRootsLocator.getInstance(project).markImportingInProgress(projectPath, false)
    }

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        GradleBuildRootsLocator.getInstance(project).markImportingInProgress(projectPath, false)
    }

    private fun ExternalSystemTaskId.isGradleRelatedTask() = /*|| type == EXECUTE_TASK*/
        projectSystemId == GradleConstants.SYSTEM_ID && type == RESOLVE_PROJECT
}
