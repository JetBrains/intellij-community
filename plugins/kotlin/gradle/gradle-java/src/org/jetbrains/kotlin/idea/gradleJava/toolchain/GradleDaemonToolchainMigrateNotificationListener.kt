// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDaemonToolchainMigrateNotificationListener: ExternalSystemTaskNotificationListener {

    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        if (!Registry.`is`("gradle.daemon.jvm.criteria.suggest.migration")) return
        if (GradleConstants.SYSTEM_ID.id != id.projectSystemId.id || id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return

        val project = id.findProject() ?: return
        val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath) ?: return

        if (!GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(projectSettings.resolveGradleVersion())) return
        if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectSettings)) return

        GradleDaemonToolchainMigrateNotification.show(project, projectPath)
    }
}